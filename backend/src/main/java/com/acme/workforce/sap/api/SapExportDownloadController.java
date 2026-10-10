package com.acme.workforce.sap.api;
import com.acme.workforce.sap.application.SapExportDownloadService;
import com.acme.workforce.identity.application.IdentityService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.*;
import java.util.UUID;
@RestController
public class SapExportDownloadController {
 public static final String XLSX="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
 private final SapExportDownloadService downloads;private final IdentityService identity;
 public SapExportDownloadController(SapExportDownloadService downloads,IdentityService identity){this.downloads=downloads;this.identity=identity;}
 @GetMapping(value="/api/v1/sap/timesheets/{id}/export.xlsx",produces=XLSX)
 public ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){
  var file=downloads.download(identity.current(jwt).getId(),id);
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(XLSX))
   .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.filename()).build().toString())
   .header("X-Content-Type-Options","nosniff").body(file.bytes());
 }
}
