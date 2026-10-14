package com.goldys.platform.api;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.goldys.platform.semantic.CategorySuggestionView;
import com.goldys.platform.semantic.ClassificationRunView;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
  void runClassificationReturns202AndDelegates() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());

    mvc.perform(
            post("/api/products/categories/runs")
                .with(authenticated(owner()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"CLUSTER\"}"))
        .andExpect(status().isAccepted());

    verify(reporting).runClassification(any(), eq("CLUSTER"));
  }

  @Test
  void runClassificationUnknownKindReturns400() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    doThrow(new IllegalArgumentException("Unknown classification run kind: BOGUS"))
        .when(reporting)
        .runClassification(any(), eq("BOGUS"));

    mvc.perform(
            post("/api/products/categories/runs")
                .with(authenticated(owner()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"BOGUS\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listRunsReturns200AndDelegates() throws Exception {
    ClassificationRunView view =
        new ClassificationRunView(
            UUID.randomUUID(),
            "FULL",
            "SUCCEEDED",
            5,
            java.time.Instant.parse("2026-10-13T00:00:00Z"),
            java.time.Instant.parse("2026-10-13T00:01:00Z"),
            null);
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(reporting.listRuns(any())).thenReturn(List.of(view));

    mvc.perform(get("/api/products/categories/runs").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].kind").value("FULL"))
        .andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
        .andExpect(jsonPath("$[0].productCount").value(5));

    verify(reporting).listRuns(any());
  }

  @Test
  void suggestionsReturns200AndList() throws Exception {
    CategorySuggestionView view =
        new CategorySuggestionView(UUID.randomUUID(), null, "Beer", "Drinks", 0, null);
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(reporting.listSuggestions(any())).thenReturn(List.of(view));

    mvc.perform(get("/api/products/categories/suggestions").with(authenticated(owner())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Beer"))
        .andExpect(jsonPath("$[0].groupName").value("Drinks"));

    verify(reporting).listSuggestions(any());
  }

  @Test
  void addSuggestionDelegates() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());

    mvc.perform(
            post("/api/products/categories/suggestions")
                .with(authenticated(owner()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cocktails\",\"groupName\":\"Drinks\"}"))
        .andExpect(status().isOk());

    verify(reporting).addSuggestion(any(), eq("Cocktails"), eq("Drinks"));
  }

  @Test
  void updateSuggestionDelegates() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    UUID id = UUID.randomUUID();

    mvc.perform(
            patch("/api/products/categories/suggestions/" + id)
                .with(authenticated(owner()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Beer\",\"groupName\":\"Drinks\"}"))
        .andExpect(status().isOk());

    verify(reporting).updateSuggestion(any(), eq(id), eq("Beer"), eq("Drinks"));
  }

  @Test
  void deleteSuggestionDelegates() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    UUID id = UUID.randomUUID();

    mvc.perform(
            delete("/api/products/categories/suggestions/" + id)
                .with(authenticated(owner()))
                .with(csrf()))
        .andExpect(status().isOk());

    verify(reporting).deleteSuggestion(any(), eq(id));
  }

  @Test
  void applySuggestionsReturns200() throws Exception {
    CategoryNode node = new CategoryNode(UUID.randomUUID(), "Beer", "Drinks", 0);
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    when(reporting.applySuggestions(any())).thenReturn(List.of(node));

    mvc.perform(
            post("/api/products/categories/suggestions/apply")
                .with(authenticated(owner()))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Beer"));

    verify(reporting).applySuggestions(any());
  }

  @Test
  void deleteCategoryDelegates() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    UUID id = UUID.randomUUID();

    mvc.perform(
            delete("/api/products/categories/categories/" + id)
                .with(authenticated(owner()))
                .with(csrf()))
        .andExpect(status().isOk());

    verify(reporting).delete(any(), eq(id));
  }

  @Test
  void updateClearsGroupWithEmptyString() throws Exception {
    when(currentUser.roleOf(any())).thenReturn(ownerRole());
    UUID id = UUID.randomUUID();

    mvc.perform(
            patch("/api/products/categories/categories/" + id)
                .with(authenticated(owner()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"groupName\":\"\"}"))
        .andExpect(status().isOk());

    verify(reporting).regroup(any(), eq(id), eq(null));
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
