package com.goldys.platform.api;

import com.goldys.platform.application.RelationalQueryApplicationService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.semantic.query.DatasetDescriptor;
import com.goldys.platform.semantic.query.FieldDescriptor;
import com.goldys.platform.semantic.query.QueryPlan;
import com.goldys.platform.semantic.query.QueryResult;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Generic, catalogue-driven relational query delivery: discover datasets and fields, and execute a
 * validated, parameterised query plan. The plan is bounded (no raw SQL, no client-controlled table
 * names); authorization is operand-level inside the compiler.
 */
@RestController
@RequestMapping("/api/query")
public class RelationalQueryController {
  private final RelationalQueryApplicationService queries;
  private final CurrentUserService currentUser;

  public RelationalQueryController(
      RelationalQueryApplicationService queries, CurrentUserService currentUser) {
    this.queries = queries;
    this.currentUser = currentUser;
  }

  @GetMapping("/datasets")
  List<DatasetDescriptor> datasets(@AuthenticationPrincipal AccountUserDetails user) {
    UserRole role = currentUser.roleOf(user);
    return queries.datasets(role);
  }

  @GetMapping("/datasets/{id}/fields")
  List<FieldDescriptor> fields(
      @PathVariable String id, @AuthenticationPrincipal AccountUserDetails user) {
    UserRole role = currentUser.roleOf(user);
    return queries.fields(role, id);
  }

  @PostMapping("/plan")
  QueryResult run(@RequestBody QueryPlan plan, @AuthenticationPrincipal AccountUserDetails user) {
    UserRole role = currentUser.roleOf(user);
    return queries.run(role, plan);
  }
}
