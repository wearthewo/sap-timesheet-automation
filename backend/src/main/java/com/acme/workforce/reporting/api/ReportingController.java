package com.acme.workforce.reporting.api;
import com.acme.workforce.reporting.api.ReportingDtos.*;
import com.acme.workforce.reporting.application.*;
import com.acme.workforce.identity.application.IdentityService;
import com.acme.workforce.common.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
@RestController @Validated @RequestMapping("/api/v1/reports/monthly")
public class ReportingController {
 private final ReportingService reports;private final ReportDownloadService downloads;private final IdentityService identity;
 public ReportingController(ReportingService reports,ReportDownloadService downloads,IdentityService identity){this.reports=reports;this.downloads=downloads;this.identity=identity;}
 @GetMapping("/summary") public MonthlyFinanceSummary summary(@AuthenticationPrincipal Jwt jwt,@Valid @ModelAttribute ReportFilter filter){return reports.summary(identity.current(jwt).getId(),filter);}
 @GetMapping("/employees") public PageResponse<EmployeeMonthlyReportRow> employees(@AuthenticationPrincipal Jwt jwt,@Valid @ModelAttribute ReportFilter filter,
 @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="25") @Min(1) @Max(100) int size){
  return PageResponse.from(reports.employees(identity.current(jwt).getId(),filter,PageRequest.of(page,size)));
 }
 @GetMapping("/projects") public PageResponse<ProjectMonthlyTotal> projects(@AuthenticationPrincipal Jwt jwt,@Valid @ModelAttribute ReportFilter filter,
 @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="25") @Min(1) @Max(100) int size){
  return PageResponse.from(reports.projects(identity.current(jwt).getId(),filter,PageRequest.of(page,size)));
 }
 @GetMapping("/employee-projects") public PageResponse<EmployeeProjectHours> employeeProjects(@AuthenticationPrincipal Jwt jwt,@Valid @ModelAttribute ReportFilter filter,
 @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="25") @Min(1) @Max(100) int size){
  return PageResponse.from(reports.employeeProjects(identity.current(jwt).getId(),filter,PageRequest.of(page,size)));
 }
 @GetMapping(value="/approved-hours.csv",produces="text/csv")
 public ResponseEntity<byte[]> csv(@AuthenticationPrincipal Jwt jwt,@Valid @ModelAttribute ReportFilter filter){return file(jwt,filter,false);}
 @GetMapping(value="/approved-hours.xlsx",produces="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
 public ResponseEntity<byte[]> xlsx(@AuthenticationPrincipal Jwt jwt,@Valid @ModelAttribute ReportFilter filter){return file(jwt,filter,true);}
 private ResponseEntity<byte[]> file(Jwt jwt,ReportFilter filter,boolean excel){
  var bytes=downloads.download(identity.current(jwt).getId(),filter,excel);
  String filename="workforce-"+(filter.effectiveMode()==com.acme.workforce.reporting.domain.ReportMode.SAP_EXPORTED?"sap-exported":"approved")+"-hours-%04d-%02d.".formatted(filter.year(),filter.month())+(excel?"xlsx":"csv");
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(filename).build().toString())
   .contentType(MediaType.parseMediaType(excel?"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":"text/csv;charset=UTF-8")).body(bytes);
 }
}
