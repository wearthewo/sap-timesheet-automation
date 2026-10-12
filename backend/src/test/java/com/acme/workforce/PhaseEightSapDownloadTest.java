package com.acme.workforce;
import com.acme.workforce.identity.application.IdentityService;
import com.acme.workforce.employee.domain.Employee;
import com.acme.workforce.employee.infrastructure.EmployeeRepository;
import com.acme.workforce.sap.domain.*;
import com.acme.workforce.sap.infrastructure.*;
import com.acme.workforce.sap.application.SapExportDownloadService;
import com.fasterxml.jackson.databind.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import java.io.ByteArrayInputStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class PhaseEightSapDownloadTest extends TenantFixtures {
 @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
 @DynamicPropertySource static void config(DynamicPropertyRegistry p){
  p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.datasource.username",postgres::getUsername);p.add("spring.datasource.password",postgres::getPassword);
  p.add("app.reporting.max-download-rows",()->"4");
 }
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate sql;@Autowired IdentityService identity;@Autowired EmployeeRepository employees;
 @Autowired SapExportDownloadService downloads;@Autowired SapExportSnapshotReader snapshots;
 @MockitoSpyBean SapExportWorkbook workbook;
 @MockitoBean JwtDecoder decoder;@MockitoBean SapTimesheetGateway gateway;
 UUID sheet,export,owner,finance;ApprovedTimesheetPayload payload;
 UUID user(String sub,String role){
  var id=identity.current(Jwt.withTokenValue("test").header("alg","RS256").subject(sub).claim("email",sub+"@example.test").claim("name",sub).build()).getId();
  member(sql,id,role);return id;
 }
 @BeforeEach void seed(){
  sql.execute("TRUNCATE app_users CASCADE");finance=user("finance","FINANCE");user("admin","ADMIN");user("manager","MANAGER");
  owner=employees.saveAndFlush(new Employee(ORGANIZATION,user("employee","EMPLOYEE"),"CURRENT-EMPLOYEE",null)).getId();
  sheet=UUID.randomUUID();export=UUID.randomUUID();
  payload=new ApprovedTimesheetPayload(sheet,"E008",2026,10,List.of(
   new ApprovedTimesheetPayload.Entry("PRJ-HISTORICAL",LocalDate.of(2026,10,5),new BigDecimal("6.25"),"Δοκιμή & \"quoted\"\n<text>"),
   new ApprovedTimesheetPayload.Entry("=HYPERLINK(\"unsafe\")",LocalDate.of(2026,10,6),new BigDecimal("1.10"),"=1+1")));
  sql.update("INSERT INTO timesheets(id,employee_id,year,month,status,total_hours,submitted_at,version) VALUES (?,?,2026,10,'APPROVED',99,now(),3)",sheet,owner);
 }
 void reserve(String state,ApprovedTimesheetPayload snapshot)throws Exception{reserveRaw(state,snapshot==null?null:json.writeValueAsString(snapshot));}
 void reserveRaw(String state,String snapshot){
  sql.update("""
   INSERT INTO sap_timesheet_exports(id,timesheet_id,status,external_reference,exported_at,failure_code,failure_message,created_at,updated_at)
   VALUES (?,?,?,CASE WHEN ?='SUCCEEDED' THEN 'SAP-TS-008' END,CASE WHEN ?='SUCCEEDED' THEN '2026-10-25T01:30:00Z'::timestamptz END,
   CASE WHEN ? IN ('FAILED','UNKNOWN') THEN 'SAP_TIMEOUT' END,CASE WHEN ? IN ('FAILED','UNKNOWN') THEN 'Safe failure' END,now(),now())
   """,export,sheet,state,state,state,state,state);
  if(snapshot!=null)sql.update("INSERT INTO sap_export_jobs(id,export_id,timesheet_id,status,operation,payload,next_attempt_at,created_at) VALUES (?,?,?,'COMPLETED','EXPORT',?::jsonb,now(),'2026-10-01T00:00:00Z')",UUID.randomUUID(),export,sheet,snapshot);
 }
 String path(){return "/api/v1/sap/timesheets/"+sheet+"/export.xlsx";}
 ResultActions download(String actor)throws Exception{return mvc.perform(get(path()).accept("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").with(jwt().jwt(j->j.subject(actor))));}
 byte[] success(String actor)throws Exception{return download(actor).andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("Content-Disposition","attachment; filename=\"sap-export-E008-2026-10.xlsx\"")).andReturn().getResponse().getContentAsByteArray();}
 @ParameterizedTest @ValueSource(strings={"finance","admin"})
 void authorizedSnapshotWorkbook(String actor)throws Exception{
  reserve("SUCCEEDED",payload);
  try(var book=new XSSFWorkbook(new ByteArrayInputStream(success(actor)))){
   assertThat(book.getNumberOfSheets()).isEqualTo(2);
   var summary=book.getSheet("Export Summary");var entries=book.getSheet("Exported Time Entries");
   assertThat(summary.getRow(1).getCell(1).getStringCellValue()).isEqualTo("E008");
   assertThat(summary.getRow(2).getCell(1).getStringCellValue()).isEqualTo("2026-10");
   assertThat(summary.getRow(3).getCell(1).getStringCellValue()).isEqualTo(sheet.toString());
   assertThat(summary.getRow(4).getCell(1).getStringCellValue()).isEqualTo(export.toString());
   assertThat(summary.getRow(5).getCell(1).getStringCellValue()).isEqualTo("SAP_TIMESHEET:"+sheet);
   assertThat(summary.getRow(6).getCell(1).getStringCellValue()).isEqualTo("SAP-TS-008");
   assertThat(summary.getRow(7).getCell(1).getStringCellValue()).isEqualTo("SUCCEEDED");
   assertThat(summary.getRow(8).getCell(1).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026,10,25,1,30));
   assertThat(summary.getRow(9).getCell(1).getNumericCellValue()).isCloseTo(7.35,within(1e-10));
   assertThat(summary.getRow(10).getCell(1).getNumericCellValue()).isEqualTo(2);
   assertThat(entries.getRow(1).getCell(0).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2026,10,5));
   assertThat(entries.getRow(1).getCell(1).getStringCellValue()).isEqualTo("PRJ-HISTORICAL");
   assertThat(entries.getRow(1).getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);
   assertThat(entries.getRow(1).getCell(3).getStringCellValue()).isEqualTo(payload.entries().getFirst().description());
   assertThat(entries.getRow(3).getCell(2).getNumericCellValue()).isCloseTo(7.35,within(1e-10));
   assertThat(entries.getPaneInformation().isFreezePane()).isTrue();
   assertThat(entries.getCTWorksheet().isSetAutoFilter()).isTrue();
   for(var tab:book)for(var row:tab)for(var cell:row)assertThat(cell.getCellType()).isNotEqualTo(CellType.FORMULA);
  }
  verifyNoInteractions(gateway);
 }
 @ParameterizedTest @ValueSource(strings={"employee","manager"})
 void unauthorizedEvenWithXlsxAccept(String actor)throws Exception{reserve("SUCCEEDED",payload);download(actor).andExpect(status().isForbidden()).andExpect(content().contentType("application/json")).andExpect(jsonPath("$.code").value("SAP_EXPORT_NOT_AUTHORIZED"));}
 @Test void unauthenticated()throws Exception{mvc.perform(get(path())).andExpect(status().isUnauthorized());}
 @ParameterizedTest @ValueSource(strings={"QUEUED","PROCESSING","FAILED","UNKNOWN"})
 void incompleteExportIsStructuredConflict(String state)throws Exception{reserve(state,payload);download("finance").andExpect(status().isConflict()).andExpect(content().contentType("application/json")).andExpect(jsonPath("$.code").value("SAP_EXPORT_NOT_SUCCEEDED"));}
 @ParameterizedTest @ValueSource(strings={"DRAFT","PENDING_APPROVAL","REJECTED"})
 void nonApprovedRejected(String state)throws Exception{
  reserve("SUCCEEDED",payload);sql.update("UPDATE timesheets SET status=?,submitted_at=CASE WHEN ?='DRAFT' THEN NULL ELSE now() END WHERE id=?",state,state,sheet);
  download("finance").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SAP_EXPORT_NOT_ALLOWED"));
 }
 @Test void absentExport404()throws Exception{download("finance").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SAP_EXPORT_NOT_FOUND"));}
 @Test void snapshotCapturedAfterSuccessIsRefused()throws Exception{
  reserve("SUCCEEDED",payload);
  sql.update("UPDATE sap_export_jobs SET created_at='2026-10-26T00:00:00Z' WHERE export_id=?",export);
  download("finance").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SAP_EXPORT_SNAPSHOT_INCOMPLETE"));
  verify(workbook,never()).generate(any());
 }
 @Test void absentSnapshot409()throws Exception{reserve("SUCCEEDED",null);download("finance").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SAP_EXPORT_SNAPSHOT_INCOMPLETE"));}
 @ParameterizedTest @ValueSource(strings={"{}","{\"entries\":[]}","{\"timesheetId\":null}","[]"})
 void corruptSnapshot409(String raw)throws Exception{reserveRaw("SUCCEEDED",raw);download("finance").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SAP_EXPORT_SNAPSHOT_INCOMPLETE"));}
 @Test void wrongSnapshotIdentityRejected()throws Exception{
  reserve("SUCCEEDED",new ApprovedTimesheetPayload(UUID.randomUUID(),"E008",2026,10,payload.entries()));
  download("finance").andExpect(status().isConflict());
 }
 @Test void blankSuccessReferenceRejected()throws Exception{
  reserve("SUCCEEDED",payload);sql.update("UPDATE sap_timesheet_exports SET external_reference=' ' WHERE id=?",export);
  download("finance").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SAP_EXPORT_SNAPSHOT_INCOMPLETE"));
 }
 @Test void currentRecordsCannotRewriteExportHistory()throws Exception{
  reserve("SUCCEEDED",payload);var first=snapshots.read(finance,sheet);
  sql.update("UPDATE employees SET employee_number='RENAMED' WHERE id=?",owner);sql.update("UPDATE timesheets SET total_hours=1,year=2027 WHERE id=?",sheet);
  var second=snapshots.read(finance,sheet);
  assertThat(second).isEqualTo(first);assertThat(second.totalHours()).isEqualByComparingTo("7.35");
  success("finance");
 }
 @Test void formattingOutsideDatabaseTransaction()throws Exception{
  reserve("SUCCEEDED",payload);
  doAnswer(i->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return i.callRealMethod();}).when(workbook).generate(any());
  assertThat(downloads.download(finance,sheet).bytes()).isNotEmpty();
 }
 @ParameterizedTest @ValueSource(strings={"=1+1","+cmd","-cmd","@SUM(A1)","\t=1","\r\n=2","Ανδρέας & \"name\"","../../employee\r\nX-Header: bad"})
 void userTextIsNeverFormulaAndFilenameSafe(String value)throws Exception{
  reserve("SUCCEEDED",new ApprovedTimesheetPayload(sheet,value,2026,10,List.of(new ApprovedTimesheetPayload.Entry(value,LocalDate.of(2026,10,1),new BigDecimal("0.10"),value))));
  var response=download("finance").andExpect(status().isOk()).andReturn().getResponse();
  assertThat(response.getHeader("Content-Disposition")).matches("attachment; filename=\"sap-export-[A-Za-z0-9_-]+-2026-10.xlsx\"");
  try(var book=new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))){
   assertThat(book.getSheetAt(0).getRow(1).getCell(1).getStringCellValue()).isEqualTo(value);
   assertThat(book.getSheetAt(1).getRow(1).getCell(3).getCellType()).isEqualTo(CellType.STRING);
   assertThat(book.getSheetAt(1).getRow(1).getCell(3).getStringCellValue()).isEqualTo(value);
  }
 }
 @Test void rowLimitEnforcedBeforeFormatting()throws Exception{
  reserve("SUCCEEDED",new ApprovedTimesheetPayload(sheet,"E008",2026,10,Collections.nCopies(5,payload.entries().getFirst())));
  download("finance").andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("SAP_EXPORT_TOO_LARGE"));
  verify(workbook,never()).generate(any());
 }
}
