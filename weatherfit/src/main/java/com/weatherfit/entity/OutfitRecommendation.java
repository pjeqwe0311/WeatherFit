
package com.weatherfit.entity;

public class OutfitRecommendation {

    private Long topId;

    private Long bottomId;

    private Long outerId;

    private Long shoesId;

    private String reason;

    public OutfitRecommendation() {
    }

    public Long getTopId() {
        return topId;
    }

    public void setTopId(Long topId) {
        this.topId = topId;
    }

    public Long getBottomId() {
        return bottomId;
    }

    public void setBottomId(Long bottomId) {
        this.bottomId = bottomId;
    }

    public Long getOuterId() {
        return outerId;
    }

    public void setOuterId(Long outerId) {
        this.outerId = outerId;
    }

    public Long getShoesId() {
        return shoesId;
    }

    public void setShoesId(Long shoesId) {
        this.shoesId = shoesId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
