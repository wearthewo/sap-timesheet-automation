import { test,expect,type Page } from "@playwright/test";
const employee=process.env.TEST_USERNAME;
const employeePassword=process.env.TEST_PASSWORD;
const manager=process.env.TEST_MANAGER_USERNAME;
const managerPassword=process.env.TEST_MANAGER_PASSWORD;
const period=process.env.TEST_APPROVAL_MONTH;
test.beforeAll(()=>{
 if(!employee || !employeePassword || !manager || !managerPassword || !period || !process.env.TEST_PROJECT_ONE) throw new Error("Set employee/manager test credentials, TEST_APPROVAL_MONTH (unstarted YYYY-MM), and TEST_PROJECT_ONE. The employee must directly report to this manager.");
});
async function login(page:Page,username:string,password:string) {
 await page.goto("/login");await page.getByRole("button",{name:"Sign in with your work account"}).click();
 await page.locator("#username").fill(username);await page.locator("#password").fill(password);await page.locator("#kc-login").click();
 await expect(page.getByRole("navigation",{name:"Main navigation"})).toBeVisible();
}
async function logout(page:Page) {
 await page.getByRole("button",{name:"Log out",exact:true}).click();await page.getByRole("button",{name:/logout/i}).click();
 await expect(page).toHaveURL(/\/login$/);
}
async function openMonth(page:Page) {
 await page.getByRole("link",{name:"Timesheets",exact:true}).click();await page.getByLabel("Choose a month").fill(period!);
 await page.getByRole("button",{name:"Open month"}).click();
}
test("employee submits, manager rejects, employee reopens and resubmits, manager approves",async({page})=>{
 test.setTimeout(180000);
 await login(page,employee!,employeePassword!);
 await expect(page.getByRole("link",{name:"Approvals",exact:true})).toHaveCount(0);
 await openMonth(page);
 await page.getByRole("button",{name:"Create draft",exact:true}).click();
 await page.getByLabel("Work date").fill(period+"-05");
 const select=page.getByRole("combobox",{name:"Project",exact:true});
 const option=select.locator("option").filter({hasText:process.env.TEST_PROJECT_ONE!});
 await expect(option).toHaveCount(1);await select.selectOption((await option.getAttribute("value"))!);
 await page.getByLabel("Hours",{exact:true}).fill("6.50");await page.getByLabel("Description").fill("Approval workflow verification");
 await page.getByRole("button",{name:"Save entry",exact:true}).click();
 await expect(page.getByTestId("monthly-total")).toContainText("6.50");
 await expect(page.getByRole("heading",{name:period+"-05",exact:true})).toBeVisible();
 await page.getByRole("button",{name:"Submit month",exact:true}).click();await page.getByRole("button",{name:"Confirm submission"}).click();
 await expect(page.getByText("PENDING_APPROVAL",{exact:true})).toBeVisible();await expect(page.getByRole("region",{name:"Entry editor"})).toHaveCount(0);
 await logout(page);
 await page.setViewportSize({width:375,height:812});
 await login(page,manager!,managerPassword!);
 await expect(page.getByRole("heading",{name:"Pending approvals",exact:true})).toBeVisible();
 await page.getByRole("link",{name:"Approvals",exact:true}).click();
 const queue=page.getByRole("region",{name:"Pending timesheets"});
 await expect(queue).toContainText("6.50 hours");
 await queue.getByRole("link").filter({hasText:"6.50 hours"}).click();
 await expect(page.getByRole("region",{name:"Review entries"})).toContainText("Approval workflow verification");
 const reviewUrl=page.url();
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
 await expect(page.getByRole("button",{name:"Log out",exact:true})).toBeVisible();
 await page.getByRole("button",{name:"Reject timesheet",exact:true}).click();
 const comment=page.getByLabel("Rejection comment (required)");
 await page.getByRole("button",{name:"Confirm rejection"}).click();
 expect(await comment.evaluate((e:HTMLTextAreaElement)=>e.validity.valueMissing)).toBe(true);
 await comment.fill("   ");await page.getByRole("button",{name:"Confirm rejection"}).click();
 await expect(page.getByRole("alert").filter({hasText:"A rejection comment is required"})).toBeVisible();
 await comment.fill("Please correct the hours to 7.25.");
 await page.getByRole("button",{name:"Confirm rejection"}).click();
 await expect(page.getByText("REJECTED",{exact:true})).toBeVisible();
 await expect(page.getByRole("region",{name:"Decision history"})).toContainText("Please correct the hours to 7.25.");
 await page.screenshot({path:"test-results/manager-rejected-mobile.png",fullPage:true});
 await logout(page);
 await login(page,employee!,employeePassword!);await openMonth(page);
 await expect(page.getByRole("region",{name:"Rejection reason"})).toContainText("Please correct the hours to 7.25.");
 await page.getByRole("button",{name:"Reopen draft",exact:true}).click();
 await expect(page.getByText("DRAFT",{exact:true})).toBeVisible();
 await page.getByRole("button",{name:/^Edit /}).click();await page.getByLabel("Hours",{exact:true}).fill("7.25");
 await page.getByRole("button",{name:"Save changes"}).click();await expect(page.getByTestId("monthly-total")).toContainText("7.25");
 await page.getByRole("button",{name:"Submit month",exact:true}).click();await page.getByRole("button",{name:"Confirm submission"}).click();
 await expect(page.getByText("PENDING_APPROVAL",{exact:true})).toBeVisible();await logout(page);
 await page.setViewportSize({width:1280,height:900});
 await login(page,manager!,managerPassword!);await page.goto(reviewUrl);
 await expect(page.getByRole("region",{name:"Review entries"})).toContainText("7.25");
 await page.getByRole("button",{name:"Approve timesheet",exact:true}).click();
 await page.getByLabel("Approval comment (optional)").fill("Correction checked.");
 await page.getByRole("button",{name:"Confirm approval"}).click();
 await expect(page.getByText("APPROVED",{exact:true})).toBeVisible();
 await expect(page.getByRole("region",{name:"Decision history"}).getByRole("listitem")).toHaveCount(2);
 await page.screenshot({path:"test-results/manager-approved-desktop.png",fullPage:true});
 await logout(page);
 await login(page,employee!,employeePassword!);await openMonth(page);
 await expect(page.getByText("APPROVED",{exact:true})).toBeVisible();
 await expect(page.getByRole("region",{name:"Entry editor"})).toHaveCount(0);
 await expect(page.getByRole("button",{name:"Reopen draft",exact:true})).toHaveCount(0);
 await expect(page.getByRole("button",{name:/^Edit /})).toHaveCount(0);
 await expect(page.getByRole("region",{name:"Decision history"}).getByRole("listitem")).toHaveCount(2);
 await page.screenshot({path:"test-results/employee-approved-desktop.png",fullPage:true});
 await logout(page);
});
