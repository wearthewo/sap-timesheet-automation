package com.acme.workforce.sap.infrastructure;
import com.acme.workforce.sap.domain.SapExportSnapshot;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Component;
import java.io.*;
import java.time.*;
import java.math.BigDecimal;
@Component
public class SapExportWorkbook {
 public byte[] generate(SapExportSnapshot snapshot){
  try(var book=new XSSFWorkbook();var out=new ByteArrayOutputStream()){
   var bold=book.createFont();bold.setBold(true);var heading=book.createCellStyle();heading.setFont(bold);
   var decimal=book.createCellStyle();decimal.setDataFormat(book.createDataFormat().getFormat("0.00"));
   var date=book.createCellStyle();date.setDataFormat(book.createDataFormat().getFormat("yyyy-mm-dd"));
   var time=book.createCellStyle();time.setDataFormat(book.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss \"UTC\""));
   var payload=snapshot.payload();var summary=book.createSheet("Export Summary");
   row(summary,0,heading,"Field","Value");
   Object[][] values={{"Employee Number",payload.employeeNumber()},{"Timesheet Period","%04d-%02d".formatted(payload.year(),payload.month())},
    {"Timesheet ID",payload.timesheetId().toString()},{"Export ID",snapshot.exportId().toString()},
    {"Stable Export Identity",payload.idempotencyKey()},{"SAP External Reference",snapshot.externalReference()},
    {"Export Status","SUCCEEDED"},{"Exported At",snapshot.exportedAt()},{"Total Hours",snapshot.totalHours()},{"Number of Entries",payload.entries().size()}};
   for(int i=0;i<values.length;i++){
    var r=summary.createRow(i+1);r.createCell(0).setCellValue((String)values[i][0]);r.getCell(0).setCellStyle(heading);
    value(r.createCell(1),values[i][1],decimal,date,time);
   }
   summary.setColumnWidth(0,28*256);summary.setColumnWidth(1,64*256);summary.createFreezePane(0,1);summary.setAutoFilter(new CellRangeAddress(0,values.length,0,1));
   var entries=book.createSheet("Exported Time Entries");row(entries,0,heading,"Work Date","Project Code","Hours","Description");
   int n=1;for(var e:payload.entries()){
    var r=entries.createRow(n++);Object[] cells={e.workDate(),e.projectCode(),e.hours(),e.description()};
    for(int i=0;i<cells.length;i++)value(r.createCell(i),cells[i],decimal,date,time);
   }
   var total=entries.createRow(n);total.createCell(0).setCellValue("Total Hours");total.getCell(0).setCellStyle(heading);
   value(total.createCell(2),snapshot.totalHours(),decimal,date,time);
   int[] widths={18,30,14,64};for(int i=0;i<widths.length;i++)entries.setColumnWidth(i,widths[i]*256);
   entries.createFreezePane(0,1);entries.setAutoFilter(new CellRangeAddress(0,n-1,0,3));
   book.write(out);return out.toByteArray();
  }catch(IOException e){throw new IllegalStateException("SAP workbook generation failed",e);}
 }
 private void row(Sheet s,int n,CellStyle style,String... headings){
  var row=s.createRow(n);for(int i=0;i<headings.length;i++){var cell=row.createCell(i);cell.setCellValue(headings[i]);cell.setCellStyle(style);}
 }
 private void value(Cell cell,Object value,CellStyle decimal,CellStyle date,CellStyle time){
  if(value==null)return;
  if(value instanceof BigDecimal number){cell.setCellValue(number.doubleValue());cell.setCellStyle(decimal);}
  else if(value instanceof Number number)cell.setCellValue(number.doubleValue());
  else if(value instanceof LocalDate d&&d.getYear()>=1900){cell.setCellValue(d.atStartOfDay());cell.setCellStyle(date);}
  else if(value instanceof Instant instant){cell.setCellValue(LocalDateTime.ofInstant(instant,ZoneOffset.UTC));cell.setCellStyle(time);}
  else cell.setCellValue(value.toString()); // Explicit STRING cells, including = + - @; never formulas.
 }
}
