package com.sequenceiq.cloudbreak.controller.validation.template;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import javax.inject.Inject;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.google.common.base.Suppliers;
import com.sequenceiq.cloudbreak.cloud.PlatformParameters;
import com.sequenceiq.cloudbreak.cloud.PlatformParametersConsts;
import com.sequenceiq.cloudbreak.cloud.model.CloudVmTypes;
import com.sequenceiq.cloudbreak.cloud.model.DiskType;
import com.sequenceiq.cloudbreak.cloud.model.Platform;
import com.sequenceiq.cloudbreak.cloud.model.VmType;
import com.sequenceiq.cloudbreak.cloud.model.VmTypeMeta;
import com.sequenceiq.cloudbreak.cloud.model.VolumeParameterConfig;
import com.sequenceiq.cloudbreak.cloud.model.VolumeParameterType;
import com.sequenceiq.cloudbreak.cloud.service.CloudParameterService;
import com.sequenceiq.cloudbreak.controller.validation.LocationService;
import com.sequenceiq.cloudbreak.converter.spi.CredentialToExtendedCloudCredentialConverter;
import com.sequenceiq.cloudbreak.domain.Template;
import com.sequenceiq.cloudbreak.domain.VolumeTemplate;
import com.sequenceiq.cloudbreak.domain.stack.Stack;
import com.sequenceiq.cloudbreak.domain.stack.instance.InstanceGroup;
import com.sequenceiq.cloudbreak.dto.credential.Credential;
import com.sequenceiq.cloudbreak.validation.ValidationResult;
import com.sequenceiq.cloudbreak.workspace.model.User;
import com.sequenceiq.common.api.type.CdpResourceType;

@Component
public class TemplateValidator {

    @Inject
    private CloudParameterService cloudParameterService;

    @Inject
    private LocationService locationService;

    @Inject
    private CredentialToExtendedCloudCredentialConverter extendedCloudCredentialConverter;

    private final Supplier<Map<Platform, Map<String, VolumeParameterType>>> diskMappings =
            Suppliers.memoize(() -> cloudParameterService.getDiskTypes().getDiskMappings());

    private final Supplier<Map<Platform, PlatformParameters>> platformParameters =
            Suppliers.memoize(() -> cloudParameterService.getPlatformParameters());

    public void validate(Credential credential, InstanceGroup ig, Stack stack,
        CdpResourceType stackType, Optional<User> user, ValidationResult.ValidationResultBuilder validationBuilder) {

        CloudVmTypes cloudVmTypes = cloudParameterService.getVmTypesV2(
                extendedCloudCredentialConverter.convert(credential, user),
                stack.getRegion(),
                stack.getPlatformVariant(),
                stackType,
                new HashMap<>());

        if (StringUtils.isEmpty(ig.getTemplate().getInstanceType())) {
            validateCustomInstanceType(ig.getTemplate(), validationBuilder);
        } else {
            VmType vmType = null;
            Platform platform = Platform.platform(ig.getTemplate().cloudPlatform());
            Map<String, Set<VmType>> machines = cloudVmTypes.getCloudVmResponses();
            String locationString = locationService.location(stack.getRegion(), ig.getAvailabilityZone());
            if (machines.containsKey(locationString) && !machines.get(locationString).isEmpty()) {
                for (VmType type : machines.get(locationString)) {
                    if (type.value().equals(ig.getTemplate().getInstanceType())) {
                        vmType = type;
                        break;
                    }
                }
                if (vmType == null) {
                    validationBuilder.error(
                            String.format("The '%s' instance type isn't supported by '%s' platform",
                                    ig.getTemplate().getInstanceType(),
                                    platform.value()));

                }
            }

            validateVolumeTemplates(ig.getTemplate(), vmType, platform, validationBuilder);
            validateMaximumVolumeSize(ig.getTemplate(), vmType, validationBuilder);
        }
    }

    private void validateVolumeTemplates(Template value, VmType vmType, Platform platform,
        ValidationResult.ValidationResultBuilder validationBuilder) {
        for (VolumeTemplate volumeTemplate : value.getVolumeTemplates()) {
            VolumeParameterType volumeParameterType = null;
            Map<Platform, Map<String, VolumeParameterType>> disks = diskMappings.get();
            if (disks.containsKey(platform) && !disks.get(platform).isEmpty()) {
                Map<String, VolumeParameterType> map = disks.get(platform);
                volumeParameterType = map.get(volumeTemplate.getVolumeType());
                if (volumeParameterType == null) {
                    validationBuilder.error(
                            String.format("The '%s' volume type isn't supported by '%s' platform", volumeTemplate.getVolumeType(), platform.value()));
                }
            }

            validateVolume(volumeTemplate, vmType, platform, volumeParameterType, validationBuilder);
        }
    }

    private void validateCustomInstanceType(Template template, ValidationResult.ValidationResultBuilder validationBuilder) {
        Map<String, Object> params = template.getAttributes().getMap();
        Platform platform = Platform.platform(template.cloudPlatform());
        PlatformParameters pps = platformParameters.get().get(platform);
        if (pps != null) {
            Boolean customInstanceType = pps.specialParameters().getSpecialParameters().get(PlatformParametersConsts.CUSTOM_INSTANCETYPE);
            if (BooleanUtils.isTrue(customInstanceType)) {
                if (params.get(PlatformParametersConsts.CUSTOM_INSTANCETYPE_CPUS) == null
                        || params.get(PlatformParametersConsts.CUSTOM_INSTANCETYPE_MEMORY) == null) {
                    validationBuilder.error(String.format("Missing 'cpus' or 'memory' param for custom instancetype on %s platform",
                            template.cloudPlatform()));
                }
            } else {
                validationBuilder.error(String.format("Custom instancetype is not supported on %s platform", template.cloudPlatform()));
            }
        }
    }

    private void validateVolume(VolumeTemplate value, VmType vmType, Platform platform,
        VolumeParameterType volumeParameterType, ValidationResult.ValidationResultBuilder validationBuilder) {
        validateVolumeType(value, platform, validationBuilder);
        validateVolumeCount(value, vmType, volumeParameterType, validationBuilder);
        validateVolumeSize(value, vmType, volumeParameterType, validationBuilder);
    }

    private void validateMaximumVolumeSize(Template value, VmType vmType, ValidationResult.ValidationResultBuilder validationBuilder) {
        if (vmType != null) {
            Object maxSize = vmType.getMetaDataValue(VmTypeMeta.MAXIMUM_PERSISTENT_DISKS_SIZE_GB);
            if (maxSize != null) {
                int fullSize = value.getVolumeTemplates().stream().mapToInt(volume -> volume.getVolumeSize() * volume.getVolumeCount()).sum();
                if (Integer.parseInt(maxSize.toString()) < fullSize) {
                    validationBuilder.error(
                            String.format("The %s platform does not support %s Gb full volume size. The maximum size of disks could be %s Gb.",
                                    value.cloudPlatform(), fullSize, maxSize));
                }
            }
        }
    }

    private void validateVolumeType(VolumeTemplate value, Platform platform, ValidationResult.ValidationResultBuilder validationBuilder) {
        DiskType diskType = DiskType.diskType(value.getVolumeType());
        Map<Platform, Collection<DiskType>> diskTypes = cloudParameterService.getDiskTypes().getDiskTypes();
        if (diskTypes.containsKey(platform) && !diskTypes.get(platform).isEmpty()) {
            if (!diskTypes.get(platform).contains(diskType)) {
                validationBuilder.error(String.format("The '%s' platform does not support '%s' volume type", platform.value(), diskType.value()));
            }
        }
    }

    private void validateVolumeCount(VolumeTemplate value, VmType vmType, VolumeParameterType volumeParameterType,
        ValidationResult.ValidationResultBuilder validationBuilder) {
        if (vmType != null && needToCheckVolume(volumeParameterType, value.getVolumeCount())) {
            VolumeParameterConfig config = vmType.getVolumeParameterbyVolumeParameterType(volumeParameterType);
            if (config != null) {
                if (value.getVolumeCount() > config.maximumNumber()) {
                    validationBuilder.error(String.format("Max allowed volume count for '%s': %s", vmType.value(), config.maximumNumber()));
                } else if (value.getVolumeCount() < config.minimumNumber()) {
                    validationBuilder.error(String.format("Min allowed volume count for '%s': %s", vmType.value(), config.minimumNumber()));
                }
            } else {
                validationBuilder.error(String.format("The '%s' instance type does not support 'Ephemeral' volume type", vmType.value()));
            }
        }
    }

    private void validateVolumeSize(VolumeTemplate value, VmType vmType, VolumeParameterType volumeParameterType,
        ValidationResult.ValidationResultBuilder validationBuilder) {
        if (vmType != null && needToCheckVolume(volumeParameterType, value.getVolumeCount())) {
            VolumeParameterConfig config = vmType.getVolumeParameterbyVolumeParameterType(volumeParameterType);
            if (config != null) {
                if (value.getVolumeSize() > config.maximumSize()) {
                    validationBuilder.error(String.format("Max allowed volume size for '%s': %s", vmType.value(), config.maximumSize()));
                } else if (value.getVolumeSize() < config.minimumSize()) {
                    validationBuilder.error(String.format("Min allowed volume size for '%s': %s", vmType.value(), config.minimumSize()));
                }
            } else {
                validationBuilder.error(String.format("The '%s' instance type does not support 'Ephemeral' volume type", vmType.value()));
            }
        }
    }

    private boolean needToCheckVolume(VolumeParameterType volumeParameterType, Object value) {
        return volumeParameterType != VolumeParameterType.EPHEMERAL || value != null;
    }
}
