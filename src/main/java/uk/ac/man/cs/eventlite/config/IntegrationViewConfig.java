package uk.ac.man.cs.eventlite.config;

import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import uk.ac.man.cs.eventlite.dao.MastodonService;

@ControllerAdvice
public class IntegrationViewConfig {
    private final String publicMapboxToken;
    private final ObjectProvider<MastodonService> mastodonService;

    public IntegrationViewConfig(@Value("${eventlite.mapbox.public-token:}") String publicMapboxToken,
                                 ObjectProvider<MastodonService> mastodonService) {
        // A secret sk. token must never reach the browser, even if misconfigured.
        this.publicMapboxToken = publicMapboxToken.startsWith("pk.") ? publicMapboxToken : "";
        this.mastodonService = mastodonService;
    }

    @ModelAttribute("mapboxToken")
    public String mapboxToken() { return publicMapboxToken; }

    @ModelAttribute("mastodonAvailable")
    public boolean mastodonAvailable() {
        MastodonService service = mastodonService.getIfAvailable();
        return service != null && service.isEnabled();
    }

    @ModelAttribute
    public void eventDates(Model model) {
        LocalDate today = LocalDate.now();
        model.addAttribute("currentDate", today);
        model.addAttribute("earliestEventDate", today.plusDays(1));
    }
}
