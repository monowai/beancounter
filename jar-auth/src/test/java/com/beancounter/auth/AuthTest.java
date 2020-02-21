package com.beancounter.auth;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.SecurityConfig;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
    MockServletContext.class,
    ResourceServerConfig.class})
@ImportAutoConfiguration({
    WebMvcAutoConfiguration.class,
    SecurityConfig.class,
    HttpMessageConvertersAutoConfiguration.class})
@WebAppConfiguration
public class AuthTest {

//  @Autowired
//  private WebApplicationContext context;
//
//  private MockMvc mvc;
//
//  @BeforeEach
//  public void setup() {
//
//    config().mockMvcConfig(
//        mockMvcConfig()
//            .dontAutomaticallyApplySpringSecurityMockMvcConfigurer()
//    );
//
//    mvc = MockMvcBuilders
//        .webAppContextSetup(context)
//        .build();
//
//
//  }
//
//  @Test
//  @WithMockUser(username="admin",roles={"USER","ADMIN"})
//  public void isSecuredSayingHello() throws Exception {
//    mvc.perform(get(("/")).with(csrf()));
//  }

  //@WithMockUser(username="admin",roles={"USER","ADMIN"})
}
