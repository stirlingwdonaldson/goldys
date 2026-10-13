package com.goldys.platform.api;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.application.ProductCategoryReportingService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.config.SecurityConfig;
import com.goldys.platform.semantic.CategoryNode;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ProductCategoryController.class)
@Import(SecurityConfig.class)
class ProductCategoryControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean ProductCategoryReportingService reporting;
  @MockitoBean CurrentUserService currentUser;

  @Test
  void taxonomyReturns200AndList() throws Exception {
    CategoryNode node = new CategoryNode(UUID.randomUUID(), "Cocktails", "Drinks", 3);
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(reporting.taxonomy(any())).thenReturn(List.of(node));

    mvc.perform(get("/api/products/categories/taxonomy").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(node.id().toString()))
        .andExpect(jsonPath("$[0].name").value("Cocktails"))
        .andExpect(jsonPath("$[0].groupName").value("Drinks"))
        .andExpect(jsonPath("$[0].productCount").value(3));
  }

  @Test
  void controllerNeverImportsCanonicalOrClassification() {
    JavaClasses classes = new ClassFileImporter().importPackages("com.goldys.platform.api");
    noClasses()
        .that()
        .haveFullyQualifiedName(ProductCategoryController.class.getName())
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..canonical..", "..classification..")
        .check(classes);
  }

  private static AccountUserDetails owner() {
    return new AccountUserDetails(
        UUID.randomUUID(), "owner@example.com", "hash", "Owner", "ALL", "OWNER", true);
  }

  private static UserRole ownerRole() {
    return new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  }

  private static RequestPostProcessor authenticated(AccountUserDetails user) {
    return authentication(
        new UsernamePasswordAuthenticationToken(user, user.passwordHash(), List.of()));
  }
}
