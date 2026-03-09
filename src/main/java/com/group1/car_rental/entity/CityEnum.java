package com.group1.car_rental.entity;

public enum CityEnum {
    HA_NOI("Hà Nội"),
    HUE("Huế"),
    HO_CHI_MINH("Hồ Chí Minh"),
    HAI_PHONG("Hải Phòng");

    private final String displayName;

    CityEnum(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static CityEnum fromString(String value) {
        for (CityEnum city : CityEnum.values()) {
            if (city.name().equalsIgnoreCase(value)) {
                return city;
            }
        }
        throw new IllegalArgumentException("Invalid city: " + value);
    }
}
