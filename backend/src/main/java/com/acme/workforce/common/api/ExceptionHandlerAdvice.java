package com.acme.workforce.common.api;

import com.acme.workforce.common.application.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.slf4j.*;
@RestControllerAdvice
public class ExceptionHandlerAdvice {
 private static final Logger log=LoggerFactory.getLogger(ExceptionHandlerAdvice.class);
 private ResponseEntity<ApiError> error(int status,String code,String message,HttpServletRequest request) {
  return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(ApiError.of(status,code,message,request.getRequestURI()));
 }
 @ExceptionHandler(DomainException.class)
 ResponseEntity<ApiError> domain(DomainException ex,HttpServletRequest request) { return error(ex.status(),ex.code(),ex.getMessage(),request); }
 @ExceptionHandler({org.springframework.web.bind.ServletRequestBindingException.class,MethodArgumentNotValidException.class,ConstraintViolationException.class,HandlerMethodValidationException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
 ResponseEntity<ApiError> validation(Exception ex,HttpServletRequest request) { return error(400,"VALIDATION_ERROR","Request fields or parameters are invalid",request); }
 @ExceptionHandler(AccessDeniedException.class)
 ResponseEntity<ApiError> forbidden(Exception ex,HttpServletRequest request) { return error(403,"FORBIDDEN","You do not have permission for this action",request); }
 @ExceptionHandler(NoResourceFoundException.class)
 ResponseEntity<ApiError> notFound(Exception ex,HttpServletRequest request) { return error(404,"NOT_FOUND","Resource not found",request); }
 @ExceptionHandler(DataIntegrityViolationException.class)
 ResponseEntity<ApiError> integrity(DataIntegrityViolationException ex,HttpServletRequest request) {
  String detail=ex.getMostSpecificCause().getMessage();
  if(detail!=null) {
   if(detail.contains("uk_timesheet_employee_month")) return error(409,"TIMESHEET_ALREADY_EXISTS","This month already has a timesheet",request);
   if(detail.contains("uk_project_code")) return error(409,"DUPLICATE_PROJECT_CODE","Project code already exists",request);
   if(detail.contains("uk_employee_number")) return error(409,"DUPLICATE_EMPLOYEE_NUMBER","Employee number already exists",request);
   if(detail.contains("uk_active_assignment")) return error(409,"DUPLICATE_ASSIGNMENT","An active assignment already exists",request);
   if(detail.contains("ck_assignment_dates")) return error(400,"INVALID_ASSIGNMENT_DATES","validTo must not be before validFrom",request);
  }
  return error(409,"DATA_CONFLICT","The request conflicts with existing data",request);
 }
 @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
 ResponseEntity<ApiError> concurrent(Exception ex,HttpServletRequest request) { return error(409,"CONCURRENT_MODIFICATION","This timesheet changed in another session. Reload it before editing.",request); }
 @ExceptionHandler(Exception.class)
 ResponseEntity<ApiError> unexpected(Exception ex,HttpServletRequest request) {
  log.error("Unexpected request failure type={} path={}",ex.getClass().getSimpleName(),request.getRequestURI());
  return error(500,"INTERNAL_ERROR","An unexpected error occurred",request);
 }
}
