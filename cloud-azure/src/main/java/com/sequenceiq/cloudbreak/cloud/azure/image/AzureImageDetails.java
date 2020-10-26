package com.sequenceiq.cloudbreak.cloud.azure.image;

public class AzureImageDetails {

    private final String imageName;

    private final String imageId;

    private final String region;

    private final String resourceGroup;

    public AzureImageDetails(String imageName, String imageId, String region, String resourceGroup) {
        this.imageName = imageName;
        this.imageId = imageId;
        this.region = region;
        this.resourceGroup = resourceGroup;
    }

    public String getImageName() {
        return imageName;
    }

    public String getImageId() {
        return imageId;
    }

    public String getRegion() {
        return region;
    }

    public String getResourceGroup() {
        return resourceGroup;
    }
}