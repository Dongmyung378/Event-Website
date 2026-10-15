package uk.ac.man.cs.eventlite.config;

import java.time.LocalDate;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.context.annotation.Import;
import uk.ac.man.cs.eventlite.dao.MastodonService;

@ControllerAdvice
@Import(TimeConfiguration.class)
public class IntegrationViewConfig {
    private final String publicMapboxToken;
    private final ObjectProvider<MastodonService> mastodonService;
    private final Clock clock;

    public IntegrationViewConfig(@Value("${eventlite.mapbox.public-token:}") String publicMapboxToken,
                                 ObjectProvider<MastodonService> mastodonService, Clock clock) {
        // A secret sk. token must never reach the browser, even if misconfigured.
        this.publicMapboxToken = publicMapboxToken.startsWith("pk.") ? publicMapboxToken : "";
        this.mastodonService = mastodonService;
        this.clock = clock;
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
        LocalDate today = LocalDate.now(clock);
        model.addAttribute("currentDate", today);
        model.addAttribute("earliestEventDate", today.plusDays(1));
    }
}
