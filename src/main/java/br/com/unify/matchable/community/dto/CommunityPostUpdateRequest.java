package br.com.unify.matchable.community.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** Missing description preserves its value; explicit null clears it. */
public class CommunityPostUpdateRequest {
    private String body;
    private String imageDescription;
    private boolean descriptionPresent;
    public CommunityPostUpdateRequest() {}
    public CommunityPostUpdateRequest(String body) { this.body = body; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String body() { return body; }
    public String getImageDescription() { return imageDescription; }
    public void setImageDescription(String value) { imageDescription = value; descriptionPresent = true; }
    @JsonIgnore public boolean isDescriptionPresent() { return descriptionPresent; }
}
