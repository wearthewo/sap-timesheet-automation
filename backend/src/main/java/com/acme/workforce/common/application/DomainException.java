package com.acme.workforce.common.application;

public class DomainException extends RuntimeException {
 private final int status; private final String code;
 public DomainException(int status,String code,String message) { super(message); this.status=status; this.code=code; }
 public int status() { return status; }
 public String code() { return code; }
 public static DomainException notFound(String resource) { return new DomainException(404,"NOT_FOUND",resource+" not found"); }
}
