package com.acme.workforce.employee.api;
import com.acme.workforce.employee.application.EmployeeAdministration;
import com.acme.workforce.employee.api.EmployeeAdminDtos.*;
import com.acme.workforce.common.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import java.util.UUID;
@RestController @Validated @RequestMapping("/api/v1/admin/employees") @PreAuthorize("@applicationAuthorization.isAdmin(authentication)")
public class EmployeeAdminController {
 private final EmployeeAdministration employees;public EmployeeAdminController(EmployeeAdministration employees){this.employees=employees;}
 @GetMapping public PageResponse<View> list(@RequestParam(defaultValue="") @Size(max=200) String search,@RequestParam(required=false) Boolean active,@RequestParam(defaultValue="number") String sort,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return PageResponse.from(employees.list(search,active,sort,PageRequest.of(page,size)));}
 @GetMapping("/{id}") public View get(@PathVariable UUID id){return employees.get(id);}
 @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED) public View create(@Valid @RequestBody Write r){return employees.create(r);}
 @PutMapping("/{id}") public View edit(@PathVariable UUID id,@Valid @RequestBody Edit r){return employees.edit(id,r);}
 @PostMapping("/{id}/status") public View state(@PathVariable UUID id,@Valid @RequestBody State r){return employees.state(id,r);}
 @PostMapping("/{id}/link") public View link(@PathVariable UUID id,@Valid @RequestBody Link r){return employees.link(id,r);}
 @DeleteMapping("/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
 public void delete(@PathVariable UUID id,@Valid @RequestBody Delete r){employees.delete(id,r.version());}
}
