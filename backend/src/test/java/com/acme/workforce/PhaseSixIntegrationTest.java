package com.acme.workforce;
import com.acme.workforce.identity.application.IdentityService;
import com.acme.workforce.employee.domain.Employee;
import com.acme.workforce.employee.infrastructure.EmployeeRepository;
import com.acme.workforce.reporting.api.*;
import com.acme.workforce.reporting.domain.*;
import com.acme.workforce.reporting.application.*;
import com.acme.workforce.reporting.infrastructure.*;
import com.fasterxml.jackson.databind.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import java.math.BigDecimal;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class PhaseSixIntegrationTest extends TenantFixtures {
 @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
 @DynamicPropertySource static void config(DynamicPropertyRegistry p){
  p.add("app.reporting.max-download-rows",()->"16");p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.datasource.username",postgres::getUsername);p.add("spring.datasource.password",postgres::getPassword);
 }
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate sql;@Autowired IdentityService identity;
 @Autowired EmployeeRepository employees;@Autowired ReportingQueries queries;@Autowired com.acme.workforce.identity.infrastructure.UserRepository users;
 @Autowired ReportingService reports;@Autowired PlatformTransactionManager tm;
 @MockitoBean JwtDecoder decoder;
 UUID finance,manager,projectA,projectB;List<UUID> sheets;String description="Worked on \"billing, API\"\nΔοκιμή";
 UUID user(String subject,String role){
  var id=identity.current(Jwt.withTokenValue("test").header("alg","RS256").claim("email_verified",true).subject(subject).claim("email",subject+"@example.test").claim("name","Name "+subject).build()).getId();
  member(sql,id,role);return id;
 }
 UUID employee(String number,boolean active,UUID managerId){
  var id=employees.saveAndFlush(new Employee(ORGANIZATION,user(number,"EMPLOYEE"),number,managerId)).getId();
  sql.update("UPDATE employees SET active=? WHERE id=?",active,id);return id;
 }
 @BeforeEach void seed(){
  sql.execute("TRUNCATE app_users,employees,projects,project_assignments,timesheets,timesheet_entries,timesheet_approvals,sap_timesheet_exports CASCADE");
  finance=user("finance","FINANCE");user("admin","ADMIN");user("employee","EMPLOYEE");user("manager","MANAGER");
  manager=employees.saveAndFlush(new Employee(ORGANIZATION,users.findByExternalSubject("manager").orElseThrow().getId(),"MANAGER",null)).getId();
  sql.update("UPDATE employees SET active=false WHERE id=?",manager);
  projectA=UUID.randomUUID();projectB=UUID.randomUUID();
  sql.update("INSERT INTO projects(organization_id,id,code,name,active) VALUES ('00000000-0000-0000-0000-000000000001',?,'PRJ-A','Alpha allocation',true),('00000000-0000-0000-0000-000000000001',?,'PRJ-B','Historical project',false)",projectA,projectB);
  employee("EMP-00",true,null);sheets=new ArrayList<>();
  String[] states={"DRAFT","PENDING_APPROVAL","REJECTED","APPROVED","APPROVED","APPROVED","APPROVED","APPROVED","APPROVED"};
  String[] sap={null,null,null,null,"QUEUED","PROCESSING","SUCCEEDED","FAILED","UNKNOWN"};
  for(int i=0;i<states.length;i++){
   var owner=employee("EMP-0"+(i+1),true,manager);var id=UUID.randomUUID();sheets.add(id);
   sql.update("INSERT INTO timesheets(id,employee_id,year,month,status,total_hours,submitted_at,version) VALUES (?,?,2026,10,?,1.25,CASE WHEN ?='DRAFT' THEN NULL ELSE now() END,3)",id,owner,states[i],states[i]);
   sql.update("INSERT INTO timesheet_entries(id,timesheet_id,project_id,work_date,hours,description) VALUES (?,?,?,'2026-10-05',1.25,?)",UUID.randomUUID(),id,projectA,description);
   if(!states[i].equals("DRAFT") && !states[i].equals("PENDING_APPROVAL")){
    if(states[i].equals("APPROVED"))decision(id,"REJECTED","Earlier rejection","2026-10-05T12:00:00Z");
    decision(id,states[i],states[i].equals("REJECTED")?"Correct hours":null,"2026-10-06T12:00:00Z");
   }
   if(sap[i]!=null)sql.update("""
    INSERT INTO sap_timesheet_exports(id,timesheet_id,status,external_reference,exported_at,failure_code,failure_message,created_at,updated_at)
    VALUES (?,?,?, ?,CASE WHEN ?='SUCCEEDED' THEN now() ELSE NULL END,?,?,now(),now())
    """,UUID.randomUUID(),id,sap[i],sap[i].equals("SUCCEEDED")?"SAP-REPORT":null,sap[i],
    Set.of("FAILED","UNKNOWN").contains(sap[i])?"SAP_UNAVAILABLE":null,Set.of("FAILED","UNKNOWN").contains(sap[i])?"Safe error":null);
  }
  sql.update("INSERT INTO timesheet_entries(id,timesheet_id,project_id,work_date,hours) VALUES (?,?,?,'2026-10-06',0.50)",UUID.randomUUID(),sheets.get(3),projectB);
  sql.update("UPDATE timesheets SET total_hours=1.75 WHERE id=?",sheets.get(3));
  var inactive=employee("INACTIVE",false,null);
  sql.update("INSERT INTO timesheets(id,employee_id,year,month,status,total_hours,submitted_at,version) VALUES (?,?,2026,10,'APPROVED',9,now(),1)",UUID.randomUUID(),inactive);
 }
 void decision(UUID sheet,String value,String comment,String time){
  sql.update("INSERT INTO timesheet_approvals(id,timesheet_id,manager_employee_id,decision,comment,decided_at) VALUES (?,?,?,?,?,?::timestamptz)",UUID.randomUUID(),sheet,manager,value,comment,time);
 }
 ResultActions getReport(String endpoint,String actor,String query)throws Exception{
  return mvc.perform(get(java.net.URI.create("/api/v1/reports/monthly/"+endpoint+"?year=2026&month=10"+query)).with(jwt().jwt(j->j.claim("email_verified",true).subject(actor))));
 }
 JsonNode result(String endpoint,String query)throws Exception{return json.readTree(getReport(endpoint,"finance",query).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());}
 @ParameterizedTest @ValueSource(strings={"finance","admin"})
 void authorizedSummary(String actor)throws Exception{getReport("summary",actor,"").andExpect(status().isOk());}
 @ParameterizedTest @CsvSource({"employee,summary","manager,summary","employee,employees","manager,projects","employee,employee-projects","manager,approved-hours.csv","employee,approved-hours.xlsx"})
 void forbidden(String actor,String endpoint)throws Exception{getReport(endpoint,actor,"").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REPORT_NOT_AUTHORIZED"));}
 @Test void anonymousRejected()throws Exception{mvc.perform(get("/api/v1/reports/monthly/summary?year=2026&month=10")).andExpect(status().isUnauthorized());}
 @Test void exactSummary()throws Exception{
  var r=result("summary","");
  assertThat(r.path("activeEmployeeCount").asInt()).isEqualTo(10);
  for(var field:List.of("noTimesheetCount","draftCount","pendingApprovalCount","rejectedCount","sapNotExportedCount","sapQueuedCount","sapProcessingCount","sapSucceededCount","sapFailedCount","sapUnknownCount"))assertThat(r.path(field).asInt()).as(field).isEqualTo(1);
  assertThat(r.path("approvedCount").asInt()).isEqualTo(6);
  assertThat(new BigDecimal(r.path("totalRecordedHours").asText())).isEqualByComparingTo("11.75");
  assertThat(new BigDecimal(r.path("totalApprovedHours").asText())).isEqualByComparingTo("8.00");
  assertThat(new BigDecimal(r.path("totalSapExportedHours").asText())).isEqualByComparingTo("1.25");
 }
 @Test void noTimesheetDerivedWithoutMutation()throws Exception{
  int before=sql.queryForObject("SELECT count(*) FROM timesheets",Integer.class);
  var row=result("employees","&timesheetStatus=NO_TIMESHEET").path("content").get(0);
  assertThat(row.path("employeeNumber").asText()).isEqualTo("EMP-00");assertThat(new BigDecimal(row.path("totalHours").asText())).isEqualByComparingTo("0");
  assertThat(row.path("timesheetId").isNull()).isTrue();assertThat(row.path("managerName").isNull()).isTrue();
  assertThat(row.path("sapState").asText()).isEqualTo("NOT_APPLICABLE");
  assertThat(sql.queryForObject("SELECT count(*) FROM timesheets",Integer.class)).isEqualTo(before);
  assertThat(Arrays.stream(com.acme.workforce.timesheet.domain.TimesheetStatus.values()).map(Enum::name)).doesNotContain("NO_TIMESHEET");
 }
 @Test void currentApprovalAndEmployeeMetadata()throws Exception{
  var row=result("employees","&employee=EMP-04").path("content").get(0);
  assertThat(row.path("employeeName").asText()).isEqualTo("Name EMP-04");assertThat(row.path("email").asText()).isEqualTo("EMP-04@example.test");
  assertThat(row.path("managerName").asText()).isEqualTo("Name manager");assertThat(row.path("latestApprovalDecision").asText()).isEqualTo("APPROVED");
  assertThat(row.path("latestApprovalTimestamp").asText()).isEqualTo("2026-10-06T12:00:00Z");assertThat(row.path("submittedAt").isNull()).isFalse();
  assertThat(new BigDecimal(row.path("totalHours").asText())).isEqualByComparingTo("1.75");assertThat(row.path("sapState").asText()).isEqualTo("NOT_EXPORTED");
 }
 @Test void pagination()throws Exception{
  assertThat(result("employees","").path("size").asInt()).isEqualTo(25);
  var first=result("employees","&size=3");var second=result("employees","&size=3&page=1");
  assertThat(first.path("totalElements").asInt()).isEqualTo(10);assertThat(first.path("totalPages").asInt()).isEqualTo(4);
  assertThat(first.path("content").get(0).path("employeeNumber").asText()).isEqualTo("EMP-00");
  assertThat(second.path("content").get(0).path("employeeNumber").asText()).isEqualTo("EMP-03");
 }
 @ParameterizedTest @ValueSource(strings={"&size=101","&page=-1","&month=13","&year=0","&timesheetStatus=SUBMITTED","&sapStatus=BAD","&mode=BAD"})
 void invalidParameters(String query)throws Exception{
  String url="/api/v1/reports/monthly/employees?year=2026&month=10"+query;
  if(query.startsWith("&month"))url="/api/v1/reports/monthly/employees?year=2026"+query;
  if(query.startsWith("&year"))url="/api/v1/reports/monthly/employees?month=10"+query;
  mvc.perform(get(url).with(jwt().jwt(j->j.claim("email_verified",true).subject("finance")))).andExpect(status().isBadRequest());
 }
 @ParameterizedTest @CsvSource({"employee=EMP-04,1","employee=Name%20EMP-04,1","employee=EMP-04%40example.test,1","project=PRJ-B,1","project=Historical,1","timesheetStatus=APPROVED,6","sapStatus=FAILED,1","sapStatus=NOT_APPLICABLE,4","employee=%25,0"})
 void filtering(String query,int expected)throws Exception{assertThat(result("employees","&"+query).path("totalElements").asInt()).isEqualTo(expected);}
 @Test void filteredPagination()throws Exception{var r=result("employees","&timesheetStatus=APPROVED&size=2&page=1");assertThat(r.path("totalElements").asInt()).isEqualTo(6);assertThat(r.path("content")).hasSize(2);}
 @Test void projectTotalsIncludeInactiveHistory()throws Exception{
  var rows=result("projects","").path("content");assertThat(rows).hasSize(2);
  assertThat(new BigDecimal(rows.get(0).path("totalHours").asText())).isEqualByComparingTo("7.50");assertThat(rows.get(0).path("employeeCount").asInt()).isEqualTo(6);
  assertThat(new BigDecimal(rows.get(1).path("totalHours").asText())).isEqualByComparingTo("0.50");
 }
 @Test void projectFilterScopesEntryTotals()throws Exception{
  assertThat(new BigDecimal(result("projects","&project=PRJ-B").path("content").get(0).path("totalHours").asText())).isEqualByComparingTo("0.50");
  assertThat(new BigDecimal(result("summary","&project=PRJ-B").path("totalApprovedHours").asText())).isEqualByComparingTo("1.75");
 }
 @Test void breakdownGroupingAndSapMode()throws Exception{
  var rows=result("employee-projects","&employee=EMP-04").path("content");assertThat(rows).hasSize(2);
  assertThat(new BigDecimal(rows.get(0).path("totalHours").asText())).isEqualByComparingTo("1.25");
  assertThat(new BigDecimal(result("projects","&mode=SAP_EXPORTED").path("content").get(0).path("totalHours").asText())).isEqualByComparingTo("1.25");
  assertThat(result("employee-projects","&mode=SAP_EXPORTED").path("totalElements").asInt()).isEqualTo(1);
 }
 @ParameterizedTest @CsvSource({"finance,csv","admin,csv","finance,xlsx","admin,xlsx"})
 void downloadAuthorizationAndHeaders(String actor,String extension)throws Exception{
  getReport("approved-hours."+extension,actor,"").andExpect(status().isOk())
   .andExpect(header().string("Content-Disposition","attachment; filename=\"workforce-approved-hours-2026-10."+extension+"\""))
   .andExpect(header().string("Cache-Control","no-store"))
   .andExpect(content().contentTypeCompatibleWith(extension.equals("csv")?"text/csv":"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
 }
 @Test void csvEscapingAndApprovedOnly()throws Exception{
  var text=getReport("approved-hours.csv","finance","").andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
  assertThat(text).contains("\"Employee Number\"","\"1.25\"","\"0.50\"","\"Worked on \"\"billing, API\"\"\nΔοκιμή\"");
  assertThat(text).doesNotContain("\"EMP-00\"","\"EMP-01\"","\"EMP-02\"","\"EMP-03\"","INACTIVE");
  assertThat(text).contains("\"EMP-04\"","\"EMP-09\"","\"APPROVED\"");
 }
 @Test void formulaTextIsNeutralized()throws Exception{
  sql.update("UPDATE timesheet_entries SET description=? WHERE timesheet_id=?","=HYPERLINK(\"bad\")\nnext",sheets.get(3));
  var bytes=getReport("approved-hours.csv","finance","&employee=EMP-04").andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
  assertThat(bytes).contains("\"'=HYPERLINK");
 }
 @Test void sapExportedDownloadIsDistinct()throws Exception{
  var r=getReport("approved-hours.csv","finance","&mode=SAP_EXPORTED").andExpect(status().isOk()).andExpect(header().string("Content-Disposition","attachment; filename=\"workforce-sap-exported-hours-2026-10.csv\"")).andReturn().getResponse();
  assertThat(r.getContentAsString()).contains("EMP-07","SAP-REPORT").doesNotContain("EMP-04","EMP-08","EMP-09");
 }
 @Test void workbookIsRealAndTyped()throws Exception{
  var bytes=getReport("approved-hours.xlsx","finance","").andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
  try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(bytes))){
   assertThat(workbook.getNumberOfSheets()).isEqualTo(5);
   assertThat(workbook.getSheet("Employees").getLastRowNum()).isEqualTo(10);
   assertThat(workbook.getSheet("Project Totals").getRow(1).getCell(2).getNumericCellValue()).isEqualTo(7.5);
   assertThat(workbook.getSheet("Employee Project Hours").getLastRowNum()).isEqualTo(7);
   var details=workbook.getSheet("Approved Hour Details");assertThat(details.getLastRowNum()).isEqualTo(7);
   assertThat(details.getRow(1).getCell(9).getCellType()).isEqualTo(CellType.NUMERIC);
   assertThat(details.getRow(1).getCell(9).getNumericCellValue()).isEqualTo(1.25);
   assertThat(details.getRow(1).getCell(9).getCellStyle().getDataFormatString()).isEqualTo("0.00");
   assertThat(DateUtil.isCellDateFormatted(details.getRow(1).getCell(8))).isTrue();
   assertThat(details.getRow(1).getCell(8).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(java.time.LocalDate.of(2026,10,5));
   assertThat(details.getRow(1).getCell(13).getStringCellValue()).isEqualTo("APPROVED");
   assertThat(details.getPaneInformation().isFreezePane()).isTrue();
   assertThat(workbook.getSheet("Summary").getRow(20).getCell(1).getNumericCellValue()).isEqualTo(11.75);
  }
 }
 @Test void emptyApprovedReportStillValidWorkbook()throws Exception{
  var bytes=getReport("approved-hours.xlsx","finance","&timesheetStatus=DRAFT").andReturn().getResponse().getContentAsByteArray();
  try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(bytes))){assertThat(workbook.getSheet("Approved Hour Details").getLastRowNum()).isZero();}
 }
 @Test void rowLimitFailsBeforeFormatting(){
  var limited=new ReportingService(queries,users,2,organizationAccess);
  assertThatThrownBy(()->new TransactionTemplate(tm).execute(s->limited.snapshot(finance,new ReportFilter(2026,10,null,null,null,null,null))))
   .isInstanceOf(com.acme.workforce.common.application.DomainException.class).hasMessageContaining("row limit");
 }
 @Test void formattingRunsOutsideDatabaseTransaction(){
  var checked=new ReportFiles(){@Override public byte[] csv(com.acme.workforce.reporting.api.ReportingDtos.ReportSnapshot s){
   assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return super.csv(s);
  }};
  var download=new ReportDownloadService(reports,checked);
  assertThat(download.download(finance,new ReportFilter(2026,10,null,null,null,null,null),false)).isNotEmpty();
 }
 @Test void oldMonthDoesNotCreateTimesheets()throws Exception{
  mvc.perform(get("/api/v1/reports/monthly/summary?year=2025&month=1").with(jwt().jwt(j->j.claim("email_verified",true).subject("finance"))))
   .andExpect(status().isOk()).andExpect(jsonPath("$.noTimesheetCount").value(10)).andExpect(jsonPath("$.totalApprovedHours").value("0.00"));
 }

 @ParameterizedTest @ValueSource(strings={"csv","xlsx"})
 void fileAcceptHeadersPreserveJsonErrors(String format)throws Exception{
  String accept=format.equals("csv")?"text/csv":"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  mvc.perform(get("/api/v1/reports/monthly/approved-hours."+format+"?year=2026&month=10").accept(accept).with(jwt().jwt(j->j.claim("email_verified",true).subject("employee"))))
   .andExpect(status().isForbidden()).andExpect(content().contentTypeCompatibleWith("application/json")).andExpect(jsonPath("$.code").value("REPORT_NOT_AUTHORIZED"));
  mvc.perform(get("/api/v1/reports/monthly/approved-hours."+format+"?year=2026&month=13").accept(accept).with(jwt().jwt(j->j.claim("email_verified",true).subject("finance"))))
   .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/json")).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
 }
 @Test void downloadLimitReturns413WithSafeJson()throws Exception{
  for(int i=0;i<17;i++)sql.update("INSERT INTO timesheet_entries(id,timesheet_id,project_id,work_date,hours) VALUES (?,?,?,'2026-10-07',1.00)",UUID.randomUUID(),sheets.get(3),projectA);
  sql.update("UPDATE timesheets SET total_hours=total_hours+17 WHERE id=?",sheets.get(3));
  for(String format:List.of("csv","xlsx")){
   String accept=format.equals("csv")?"text/csv":"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
   mvc.perform(get("/api/v1/reports/monthly/approved-hours."+format+"?year=2026&month=10").accept(accept).with(jwt().jwt(j->j.claim("email_verified",true).subject("finance"))))
    .andExpect(status().isPayloadTooLarge()).andExpect(content().contentTypeCompatibleWith("application/json")).andExpect(jsonPath("$.code").value("REPORT_TOO_LARGE"));
  }
 }

}
