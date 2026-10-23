package com.goldys.platform.api;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.goldys.platform.application.RecipeCompositionReportingService;
import com.goldys.platform.auth.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RecipeCompositionControllerTest {
  @Test
  void absentSnapshotsHaveAnExplicitOptionalResponseContract() throws Exception {
    var mvc =
        MockMvcBuilders.standaloneSetup(
                new RecipeCompositionController(
                    mock(RecipeCompositionReportingService.class), mock(CurrentUserService.class)))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();
    mvc.perform(get("/api/recipes/463/composition")).andExpect(status().isNoContent());
    mvc.perform(get("/api/recipes/463/details")).andExpect(status().isNoContent());
    mvc.perform(get("/api/recipes/folders")).andExpect(status().isNoContent());
  }
}
