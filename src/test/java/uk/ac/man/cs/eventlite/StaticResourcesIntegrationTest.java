package uk.ac.man.cs.eventlite;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Checks the real classpath assets, resource resolver, and anonymous security rules together. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaticResourcesIntegrationTest {
    private static final String BOOTSTRAP = "/webjars/bootstrap/css/bootstrap.min.css";
    private static final String FONT_AWESOME = "/webjars/font-awesome/css/all.min.css";

    @Autowired private MockMvc mvc;

    @Test
    void originalUnversionedStylesheetUrlsRemainPublicAndServeActualCss() throws Exception {
        mvc.perform(get(BOOTSTRAP))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("text/css")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(".container")));
        mvc.perform(get(FONT_AWESOME))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("text/css")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("fa-solid-900.woff2")));
    }

    @Test
    void fontAwesomeRelativeWoff2UrlsResolveToActualFontFiles() throws Exception {
        String css = mvc.perform(get(FONT_AWESOME)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        Matcher urls = Pattern.compile("url\\([\\\"']?([^\\\"'\\)]+\\.woff2)[\\\"']?\\)").matcher(css);
        Set<String> paths = new LinkedHashSet<>();
        URI base = URI.create("http://localhost" + FONT_AWESOME);
        while (urls.find()) { paths.add(base.resolve(urls.group(1)).getPath()); }
        assertFalse(paths.isEmpty(), "The actual stylesheet must reference bundled WOFF2 fonts");
        for (String path : paths) {
            byte[] font = mvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
            assertTrue(font.length > 4, "Font asset is empty: " + path);
            assertEquals("wOF2", new String(font, 0, 4, StandardCharsets.US_ASCII),
                "A font URL must return WOFF2 bytes rather than an HTML fallback: " + path);
        }
    }
}
