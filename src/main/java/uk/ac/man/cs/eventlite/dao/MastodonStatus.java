package uk.ac.man.cs.eventlite.dao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The small public status shape used by the social-feed view. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MastodonStatus {
    private String content;
    private String url;
    @JsonProperty("created_at")
    private String createdAt;
    private String visibility;
    private Account account;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Account {
        private String id;
        private String username;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
    }
}
