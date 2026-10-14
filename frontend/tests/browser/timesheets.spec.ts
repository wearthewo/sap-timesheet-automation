import { fixture } from "./fixtures";
import { test, expect, type Page } from "@playwright/test";
const env = fixture("timesheets");
const username = env.TEST_USERNAME;
const password = env.TEST_PASSWORD;
const period = env.TEST_MONTH;
const firstProject = env.TEST_PROJECT_ONE;
const secondProject = env.TEST_PROJECT_TWO;
test.beforeAll(() => {
 if (!username || !password || !period || !firstProject || !secondProject) throw new Error("Set TEST_USERNAME, TEST_PASSWORD, TEST_MONTH (YYYY-MM), TEST_PROJECT_ONE and TEST_PROJECT_TWO. Use a dedicated local employee and an unstarted month.");
});
async function login(page: Page) {
 await page.goto("/login");
 await page.getByRole("button",{name:"Sign in with your work account"}).click();
 await page.locator("#username").fill(username!);
 await page.locator("#password").fill(password!);
 await page.locator("#kc-login").click();
 await expect(page.getByRole("navigation",{name:"Main navigation"})).toBeVisible();
}
async function logout(page: Page) {
 await page.getByRole("button",{name:"Log out",exact:true}).click();
 await page.getByRole("button",{name:/logout/i}).click();
 await expect(page).toHaveURL(/\/login$/);
 await expect(page.getByRole("button",{name:"Sign in with your work account"})).toBeVisible();
}
async function noOverflow(page: Page) {
 expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
}
test("employee creates, edits, deletes, totals, submits and reviews a month", async ({page,context}) => {
 await login(page);
 await page.getByRole("link",{name:"Timesheets",exact:true}).click();
 await expect(page.getByRole("heading",{name:"Timesheets",exact:true})).toBeVisible();
 await page.getByLabel("Choose a month").fill(period!);
 await page.getByRole("button",{name:"Open month"}).click();
 await page.getByRole("button",{name:"Create draft"}).click();
 await expect(page.getByRole("region",{name:"Entry editor"})).toBeVisible();
 const workDate = env.TEST_WORK_DATE ?? period+"-05";
 const project = page.getByRole("combobox",{name:"Project",exact:true});
 async function add(code: string,hours: string,description: string) {
  await page.getByLabel("Work date").fill(workDate);
  await expect(project.locator("option").filter({hasText:code})).toHaveCount(1);
  const value = await project.locator("option").filter({hasText:code}).getAttribute("value");
  await project.selectOption(value!);
  await page.getByLabel("Hours",{exact:true}).fill(hours);
  await page.getByLabel("Description").fill(description);
  await page.getByRole("button",{name:"Save entry",exact:true}).click();
  await expect(page.getByRole("status")).toHaveText("Draft saved.");
  await expect(page.getByRole("button",{name:"Edit "+code+" "+workDate,exact:true})).toBeVisible();
 }
 await add(firstProject!,"6.25","Browser verification A");
 await add(secondProject!,"2.50","Browser verification B");
 await expect(page.getByTestId("monthly-total")).toContainText("8.75");
 await expect(page.getByTestId("daily-total")).toHaveText("8.75 hours total");
 await page.getByRole("button",{name:"Edit "+firstProject+" "+workDate,exact:true}).click();
 await page.getByLabel("Hours",{exact:true}).fill("5.50");
 await page.getByRole("button",{name:"Save changes"}).click();
 await expect(page.getByTestId("monthly-total")).toContainText("8.00");
 // A second browser tab must not overwrite a newer draft.
 const stale = await context.newPage();
 await stale.goto(page.url());
 await expect(stale.getByTestId("monthly-total")).toContainText("8.00");
 await page.getByRole("button",{name:"Delete "+secondProject+" "+workDate,exact:true}).click();
 await expect(page.getByTestId("monthly-total")).toContainText("5.50");
 await stale.getByRole("button",{name:"Delete "+firstProject+" "+workDate,exact:true}).click();
 await expect(stale.getByRole("alert").filter({hasText:"another session"})).toContainText("another session");
 await stale.close();
 await add(secondProject!,"1.25","Browser verification restored");
 await expect(page.getByTestId("monthly-total")).toContainText("6.75");
 await page.setViewportSize({width:375,height:812});
 await noOverflow(page);
 await expect(page.getByRole("link",{name:"Timesheets",exact:true})).toBeVisible();
 await expect(page.getByRole("button",{name:"Log out",exact:true})).toBeVisible();
 await page.screenshot({path:test.info().outputPath("draft-mobile.png"),fullPage:true});
 await page.setViewportSize({width:1280,height:900});
 await page.getByRole("button",{name:"Submit month",exact:true}).click();
 await page.getByRole("button",{name:"Confirm submission"}).click();
 await expect(page.getByText(/^Read-only · Submitted/)).toBeVisible();
 await expect(page.getByRole("region",{name:"Entry editor"})).toHaveCount(0);
 await expect(page.getByRole("button",{name:/^Edit /})).toHaveCount(0);
 await expect(page.getByRole("button",{name:/^Delete /})).toHaveCount(0);
 await expect(page.getByTestId("monthly-total")).toContainText("6.75");
 await page.screenshot({path:test.info().outputPath("submitted-desktop.png"),fullPage:true});
 await page.getByRole("link",{name:"All timesheets",exact:false}).click();
 await expect(page.getByRole("region",{name:"Your monthly timesheets"})).toContainText("PENDING_APPROVAL");
 await expect(page.getByRole("region",{name:"Your monthly timesheets"})).toContainText("6.75 h");
 await logout(page);
});
test("mobile employee can review submitted month and log out", async ({page}) => {
 const period = env.TEST_MOBILE_MONTH;
 await page.setViewportSize({width:375,height:812});
 await login(page);
 await page.getByRole("link",{name:"Timesheets",exact:true}).click();
 await page.getByLabel("Choose a month").fill(period!);
 await page.getByRole("button",{name:"Open month"}).click();
 await expect(page.getByText(/^Read-only · Submitted/)).toBeVisible();
 await expect(page.getByRole("region",{name:"Entry editor"})).toHaveCount(0);
 await noOverflow(page);
 await page.screenshot({path:test.info().outputPath("submitted-mobile.png"),fullPage:true});
 await logout(page);
});
