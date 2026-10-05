package com.acme.workforce.timesheet.application;

import com.acme.workforce.common.application.DomainException;
import com.acme.workforce.employee.domain.Employee;
import com.acme.workforce.employee.infrastructure.EmployeeRepository;
import com.acme.workforce.project.domain.Project;
import com.acme.workforce.project.infrastructure.*;
import com.acme.workforce.timesheet.api.TimesheetDtos.*;
import com.acme.workforce.timesheet.domain.*;
import com.acme.workforce.timesheet.infrastructure.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class TimesheetService {
    private static final Logger log = LoggerFactory.getLogger(TimesheetService.class);
    private final TimesheetRepository sheets;
    private final TimesheetEntryRepository entries;
    private final EmployeeRepository employees;
    private final ProjectRepository projects;
    private final AssignmentRepository assignments;
    private final TimesheetPolicy policy;

    public TimesheetService(TimesheetRepository sheets, TimesheetEntryRepository entries,
            EmployeeRepository employees, ProjectRepository projects,
            AssignmentRepository assignments, TimesheetPolicy policy) {
        this.sheets = sheets; this.entries = entries; this.employees = employees;
        this.projects = projects; this.assignments = assignments; this.policy = policy;
    }
    private Employee employee(UUID userId, boolean writing) {
        var employee = employees.findByUserId(userId).orElseThrow(() ->
                new DomainException(403, "EMPLOYEE_REQUIRED", "An employee profile is required. Contact your administrator."));
        if (writing && !employee.isActive()) throw new DomainException(403, "EMPLOYEE_INACTIVE", "Your employee profile is inactive");
        return employee;
    }
    private Timesheet owned(UUID userId, UUID id, boolean writing) {
        return sheets.findByIdAndEmployeeId(id, employee(userId, writing).getId())
                .orElseThrow(() -> new DomainException(404, "TIMESHEET_NOT_FOUND", "Timesheet not found"));
    }
    private Timesheet editable(UUID userId, UUID id, long version) {
        var sheet = owned(userId, id, true);
        if (sheet.getStatus() != TimesheetStatus.DRAFT) {
            throw new DomainException(409, "TIMESHEET_ALREADY_SUBMITTED", "Submitted timesheets are read-only");
        }
        if (sheet.getVersion() != version) throw new DomainException(409, "CONCURRENT_MODIFICATION", "This timesheet changed in another session. Reload it before editing.");
        return sheet;
    }
    public Page<Summary> list(UUID userId, Pageable pageable) {
        return sheets.findByEmployeeId(employee(userId, false).getId(), pageable).map(this::summary);
    }
    public Detail month(UUID userId, int year, int month) {
        var sheet = sheets.findByEmployeeIdAndYearAndMonth(employee(userId, false).getId(), year, month)
                .orElseThrow(() -> new DomainException(404, "TIMESHEET_NOT_FOUND", "This month has no timesheet yet"));
        return detail(sheet, entries.findByTimesheetIdOrderByWorkDateAscIdAsc(sheet.getId()));
    }
    @Transactional
    public Detail create(UUID userId, CreateTimesheet request) {
        var sheet = sheets.saveAndFlush(new Timesheet(employee(userId, true).getId(), request.year(), request.month()));
        log.info("Created timesheet id={}", sheet.getId());
        return detail(sheet, List.of());
    }
    public List<ProjectReference> available(UUID userId, UUID id, LocalDate date) {
        var sheet = owned(userId, id, false);
        policy.checkDate(sheet, date);
        if (!employee(userId, false).isActive()) return List.of();
        var assigned = assignments.findByEmployeeId(sheet.getEmployeeId());
        var ids = assigned.stream().map(a -> a.getProjectId()).collect(Collectors.toSet());
        return projects.findAllById(ids).stream().filter(p -> policy.available(p, assigned, date))
                .sorted(Comparator.comparing(Project::getCode).thenComparing(Project::getId))
                .map(this::projectReference).toList();
    }
    @Transactional
    public Detail add(UUID userId, UUID id, long version, WriteEntry request) {
        var sheet = editable(userId, id, version);
        validateEntry(sheet, request);
        var current = new ArrayList<>(entries.findByTimesheetIdOrderByWorkDateAscIdAsc(id));
        var entry = new TimesheetEntry(id, request.projectId(), request.workDate(), request.hours(), request.description());
        current.add(entry);
        recalculate(sheet, current);
        entries.save(entry);
        sheets.flush(); // Includes entry changes and the aggregate optimistic version in this transaction.
        return detail(sheet, current);
    }
    @Transactional
    public Detail edit(UUID userId, UUID id, UUID entryId, long version, WriteEntry request) {
        var sheet = editable(userId, id, version);
        var current = entries.findByTimesheetIdOrderByWorkDateAscIdAsc(id);
        var entry = current.stream().filter(e -> e.getId().equals(entryId)).findFirst()
                .orElseThrow(() -> new DomainException(404, "ENTRY_NOT_FOUND", "Timesheet entry not found"));
        validateEntry(sheet, request);
        entry.edit(request.projectId(), request.workDate(), request.hours(), request.description());
        recalculate(sheet, current);
        sheets.flush();
        return detail(sheet, current);
    }
    @Transactional
    public Detail delete(UUID userId, UUID id, UUID entryId, long version) {
        var sheet = editable(userId, id, version);
        var current = new ArrayList<>(entries.findByTimesheetIdOrderByWorkDateAscIdAsc(id));
        var entry = current.stream().filter(e -> e.getId().equals(entryId)).findFirst()
                .orElseThrow(() -> new DomainException(404, "ENTRY_NOT_FOUND", "Timesheet entry not found"));
        current.remove(entry);
        entries.delete(entry);
        recalculate(sheet, current);
        sheets.flush();
        return detail(sheet, current);
    }
    @Transactional
    public Detail submit(UUID userId, UUID id, long version) {
        var sheet = editable(userId, id, version);
        var current = entries.findByTimesheetIdOrderByWorkDateAscIdAsc(id);
        if (current.isEmpty()) throw new DomainException(409, "TIMESHEET_EMPTY", "Add at least one entry before submitting");
        var projectMap = projectMap(current);
        var assigned = assignments.findByEmployeeId(sheet.getEmployeeId());
        for (var entry : current) {
            policy.checkDate(sheet, entry.getWorkDate());
            policy.checkProject(projectMap.get(entry.getProjectId()), assigned, entry.getWorkDate());
        }
        recalculate(sheet, current);
        sheet.submit();
        sheets.flush();
        log.info("Submitted timesheet id={} version={}", sheet.getId(), sheet.getVersion());
        return detail(sheet, current);
    }
    private void validateEntry(Timesheet sheet, WriteEntry request) {
        policy.checkDate(sheet, request.workDate());
        var project = projects.findById(request.projectId()).orElseThrow(() -> DomainException.notFound("Project"));
        policy.checkProject(project, assignments.findByEmployeeId(sheet.getEmployeeId()), request.workDate());
    }
    private void recalculate(Timesheet sheet, List<TimesheetEntry> current) {
        var totals = policy.totals(current);
        sheet.recalculate(totals.values().stream().reduce(new BigDecimal("0.00"), BigDecimal::add));
    }
    private Map<UUID, Project> projectMap(List<TimesheetEntry> current) {
        return projects.findAllById(current.stream().map(TimesheetEntry::getProjectId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Project::getId, Function.identity()));
    }
    private ProjectReference projectReference(Project p) { return new ProjectReference(p.getId(), p.getCode(), p.getName()); }
    private Summary summary(Timesheet s) { return new Summary(s.getId(), s.getYear(), s.getMonth(), s.getStatus(), s.getTotalHours(), s.getSubmittedAt(), s.getVersion()); }
    private Detail detail(Timesheet sheet, List<TimesheetEntry> current) {
        var projectMap = projectMap(current);
        var views = current.stream().sorted(Comparator.comparing(TimesheetEntry::getWorkDate).thenComparing(TimesheetEntry::getId))
                .map(e -> new EntryView(e.getId(), projectReference(projectMap.get(e.getProjectId())), e.getWorkDate(), e.getHours(), e.getDescription())).toList();
        // Reads retain historical totals even if a later configuration lowers the daily limit.
        SortedMap<LocalDate, BigDecimal> daily = new TreeMap<>();
        current.forEach(e -> daily.merge(e.getWorkDate(), e.getHours(), BigDecimal::add));
        return new Detail(sheet.getId(), sheet.getYear(), sheet.getMonth(), sheet.getStatus(),
                sheet.getTotalHours(), sheet.getSubmittedAt(), sheet.getVersion(), views,
                daily.entrySet().stream().map(d -> new DailyTotal(d.getKey(), d.getValue())).toList(), policy.maximum());
    }
}
