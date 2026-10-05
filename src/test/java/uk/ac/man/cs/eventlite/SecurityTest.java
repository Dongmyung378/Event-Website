package uk.ac.man.cs.eventlite;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import uk.ac.man.cs.eventlite.config.Security;

@ExtendWith(SpringExtension.class)
@WebMvcTest(value = EventLite.class, properties = {"eventlite.admin.username=test-admin", "eventlite.admin.password=test-password"})
@Import(Security.class)
public class SecurityTest {

	@Autowired
	private MockMvc mvc;

	@Test
	public void getSignInForm() throws Exception {
		mvc.perform(get("/sign-in").accept(MediaType.TEXT_HTML)).andExpect(status().isOk());
	}

	@Test
	public void postSignInNoData() throws Exception {
		mvc.perform(post("/sign-in").accept(MediaType.TEXT_HTML).with(csrf()))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/sign-in?error"));
	}

	@Test
	public void postSignInBadPassword() throws Exception {
		mvc.perform(post("/sign-in").accept(MediaType.TEXT_HTML)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.param("username", "test-admin")
				.param("password", "H")
				.with(csrf()))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/sign-in?error"));
	}

	@Test
	public void postSignInBadUser() throws Exception {
		mvc.perform(post("/sign-in").accept(MediaType.TEXT_HTML)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.param("username", "R")
				.param("password", "test-password")
				.with(csrf()))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/sign-in?error"));
	}

	@Test
	public void postSignIn() throws Exception {
		mvc.perform(post("/sign-in").accept(MediaType.TEXT_HTML)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.param("username", "test-admin")
				.param("password", "test-password")
				.with(csrf()))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/"));
	}

	@Test
	public void postSignOut() throws Exception {
		mvc.perform(post("/sign-out").accept(MediaType.TEXT_HTML).with(csrf()))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/"));
	}
}
