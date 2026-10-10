package com.acme.workforce;
import com.acme.workforce.identity.application.IdentityService;
import com.acme.workforce.employee.domain.Employee;
import com.acme.workforce.employee.infrastructure.EmployeeRepository;
import com.acme.workforce.sap.domain.*;
import com.acme.workforce.sap.application.SapExportWorker;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import java.util.concurrent.*;
import java.math.BigDecimal;
import java.io.ByteArrayInputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class PhaseEightBusinessTest {
 @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
 @DynamicPropertySource static void config(DynamicPropertyRegistry p){p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.datasource.username",postgres::getUsername);p.add("spring.datasource.password",postgres::getPassword);}
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate sql;@Autowired IdentityService identity;@Autowired EmployeeRepository employees;@Autowired SapExportWorker worker;
 @MockitoBean JwtDecoder decoder;@MockitoBean SapTimesheetGateway gateway;
 UUID owner,manager,a,b,unassigned;
 UUID user(String sub,String role){
  var id=identity.current(Jwt.withTokenValue("test").header("alg","RS256").subject(sub).claim("email",sub+"@example.test").claim("name",sub).build()).getId();
  sql.update("UPDATE app_users SET role=? WHERE id=?",role,id);return id;
 }
 @BeforeEach void seed(){
  sql.execute("TRUNCATE app_users,projects CASCADE");user("finance","FINANCE");user("admin","ADMIN");
  manager=employees.saveAndFlush(new Employee(user("manager","MANAGER"),"M008",null)).getId();
  owner=employees.saveAndFlush(new Employee(user("employee","EMPLOYEE"),"E008",manager)).getId();
  employees.saveAndFlush(new Employee(user("other","EMPLOYEE"),"OTHER",null));
  employees.saveAndFlush(new Employee(user("unrelated","MANAGER"),"UNRELATED",null));
  a=UUID.randomUUID();b=UUID.randomUUID();unassigned=UUID.randomUUID();
  sql.update("INSERT INTO projects(id,code,name,active) VALUES (?,'A','Alpha',true),(?,'B','Beta',true),(?,'C','Unassigned',true)",a,b,unassigned);
  for(var id:List.of(a,b))sql.update("INSERT INTO project_assignments(id,employee_id,project_id,valid_from,active) VALUES (?,?,?,'2026-01-01',true)",UUID.randomUUID(),owner,id);
  when(gateway.export(any())).thenReturn(new SapExportResult("SAP-TS-BUSINESS"));
 }
 ResultActions req(MockHttpServletRequestBuilder r,String who)throws Exception{return mvc.perform(r.with(jwt().jwt(j->j.subject(who))));}
 JsonNode data(ResultActions r)throws Exception{return json.readTree(r.andReturn().getResponse().getContentAsByteArray());}
 MockHttpServletRequestBuilder createRequest(){return post("/api/v1/timesheets").contentType("application/json").content("{\"year\":2026,\"month\":10}");}
 JsonNode create()throws Exception{return data(req(createRequest(),"employee").andExpect(status().isCreated()));}
 String base(JsonNode s){return "/api/v1/timesheets/"+s.get("id").asText();}
 MockHttpServletRequestBuilder add(JsonNode s,UUID project,String date,String hours)throws Exception{return post(base(s)+"/entries").header("X-Timesheet-Version",s.get("version").asLong()).contentType("application/json").content(json.writeValueAsString(Map.of("projectId",project,"workDate",date,"hours",hours,"description","Δοκιμή & =text")));}
 MockHttpServletRequestBuilder transition(JsonNode s,String action){return post(base(s)+"/"+action).header("X-Timesheet-Version",s.get("version").asLong());}
 ResultActions decide(JsonNode s,String who,String action)throws Exception{return req(post("/api/v1/approvals/timesheets/"+s.get("id").asText()+"/"+action).header("X-Timesheet-Version",s.get("version").asLong()).contentType("application/json").content("{\"comment\":\"Correct & review\\nΔοκιμή\"}"),who);}
 JsonNode pending()throws Exception{var s=create();s=data(req(add(s,a,"2026-10-05","6.25"),"employee").andExpect(status().isCreated()));return data(req(transition(s,"submit"),"employee").andExpect(status().isOk()));}
 List<Integer> race(Callable<Integer> one,Callable<Integer> two)throws Exception{
  var gate=new CyclicBarrier(2);
  try(var pool=Executors.newFixedThreadPool(2)){
   var x=pool.submit(()->{gate.await(10,TimeUnit.SECONDS);return one.call();});
   var y=pool.submit(()->{gate.await(10,TimeUnit.SECONDS);return two.call();});
   return List.of(x.get(20,TimeUnit.SECONDS),y.get(20,TimeUnit.SECONDS));
  }
 }
 @Test void simultaneousMonthlyCreationHasOneWinner()throws Exception{
  assertThat(race(()->req(createRequest(),"employee").andReturn().getResponse().getStatus(),()->req(createRequest(),"employee").andReturn().getResponse().getStatus())).containsExactlyInAnyOrder(201,409);
  assertThat(sql.queryForObject("SELECT count(*) FROM timesheets",Integer.class)).isEqualTo(1);
 }
 @Test void simultaneousSubmissionsHaveOneWinner()throws Exception{
  var draft=data(req(add(create(),a,"2026-10-05","1.25"),"employee"));
  assertThat(race(()->req(transition(draft,"submit"),"employee").andReturn().getResponse().getStatus(),()->req(transition(draft,"submit"),"employee").andReturn().getResponse().getStatus())).containsExactlyInAnyOrder(200,409);
  assertThat(sql.queryForObject("SELECT status FROM timesheets",String.class)).isEqualTo("PENDING_APPROVAL");
  assertThat(sql.queryForObject("SELECT total_hours FROM timesheets",BigDecimal.class)).isEqualByComparingTo("1.25");
 }
 @Test void concurrentApproveAndRejectHaveOneDecisionAndIntent()throws Exception{
  var s=pending();
  assertThat(race(()->decide(s,"manager","approve").andReturn().getResponse().getStatus(),()->decide(s,"manager","reject").andReturn().getResponse().getStatus())).containsExactlyInAnyOrder(200,409);
  assertThat(sql.queryForObject("SELECT count(*) FROM timesheet_approvals",Integer.class)).isEqualTo(1);
  assertThat(sql.queryForObject("SELECT count(*) FROM notifications",Integer.class)).isEqualTo(1);
  assertThat(sql.queryForObject("SELECT status FROM timesheets",String.class)).isEqualTo(sql.queryForObject("SELECT decision FROM timesheet_approvals",String.class));
 }
 @ParameterizedTest @ValueSource(strings={"employee","finance","admin","unrelated"})
 void noManagerOverride(String who)throws Exception{decide(pending(),who,"approve").andExpect(status().isForbidden());assertThat(sql.queryForObject("SELECT count(*) FROM timesheet_approvals",Integer.class)).isZero();}
 @Test void completeBusinessLifecycleAndReportingAgreeWithExport()throws Exception{
  var s=create();s=data(req(add(s,a,"2026-10-05","6.25"),"employee").andExpect(status().isCreated()));
  s=data(req(add(s,b,"2026-10-05","1.10"),"employee").andExpect(status().isCreated()));
  req(add(s,a,"2026-11-01","1"),"employee").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_WORK_DATE"));
  req(add(s,unassigned,"2026-10-05","1"),"employee").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PROJECT_NOT_ASSIGNED"));
  req(add(s,a,"2026-10-05","24"),"employee").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DAILY_HOURS_EXCEEDED"));
  s=data(req(add(s,b,"2026-10-06","2"),"employee"));var extra=s.get("entries").get(2).get("id").asText();
  var edit=put(base(s)+"/entries/"+extra).header("X-Timesheet-Version",s.get("version").asLong()).contentType("application/json").content(json.writeValueAsString(Map.of("projectId",b,"workDate","2026-10-06","hours","3.25")));
  s=data(req(edit,"employee").andExpect(status().isOk()));
  s=data(req(delete(base(s)+"/entries/"+extra).header("X-Timesheet-Version",s.get("version").asLong()),"employee").andExpect(status().isOk()));
  assertThat(s.get("totalHours").asText()).isEqualTo("7.35");assertThat(s.get("dailyTotals").get(0).get("hours").asText()).isEqualTo("7.35");
  s=data(req(transition(s,"submit"),"employee"));assertThat(s.get("status").asText()).isEqualTo("PENDING_APPROVAL");
  req(add(s,a,"2026-10-05","1"),"employee").andExpect(status().isConflict());
  req(get("/api/v1/approvals/pending"),"unrelated").andExpect(jsonPath("$.totalElements").value(0));
  var rejected=data(decide(s,"manager","reject").andExpect(status().isOk())).get("timesheet");
  s=data(req(transition(rejected,"reopen"),"employee"));s=data(req(transition(s,"submit"),"employee"));
  s=data(decide(s,"manager","approve").andExpect(status().isOk())).get("timesheet");
  assertThat(s.get("approvalHistory")).hasSize(2);assertThat(s.get("status").asText()).isEqualTo("APPROVED");
  req(add(s,a,"2026-10-05","1"),"employee").andExpect(status().isConflict());
  req(get("/api/v1/notifications"),"employee").andExpect(jsonPath("$.totalElements").value(2));
  req(get("/api/v1/notifications"),"other").andExpect(jsonPath("$.totalElements").value(0));
  String sap="/api/v1/sap/timesheets/"+s.get("id").asText();
  req(post(sap+"/export").header("X-Timesheet-Version",s.get("version").asLong()),"finance").andExpect(status().isAccepted());
  worker.runOnce();
  req(get(sap+"/export"),"finance").andExpect(jsonPath("$.status").value("SUCCEEDED")).andExpect(jsonPath("$.externalReference").value("SAP-TS-BUSINESS"));
  req(post(sap+"/export").header("X-Timesheet-Version",s.get("version").asLong()),"finance").andExpect(status().isAccepted());worker.runOnce();
  verify(gateway,times(1)).export(argThat(p->p.employeeNumber().equals("E008")&&p.entries().size()==2));
  var bytes=req(get(sap+"/export.xlsx"),"finance").andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
  try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){assertThat(book.getSheetAt(0).getRow(9).getCell(1).getNumericCellValue()).isCloseTo(7.35,within(1e-10));}
  String reports="/api/v1/reports/monthly/";
  req(get(reports+"summary?year=2026&month=10&employee=E008"),"finance").andExpect(jsonPath("$.totalApprovedHours").value("7.35")).andExpect(jsonPath("$.totalSapExportedHours").value("7.35"));
  for(String format:List.of("csv","xlsx"))req(get(reports+"approved-hours."+format+"?year=2026&month=10&employee=E008&mode=SAP_EXPORTED"),"finance").andExpect(status().isOk());
 }
}
