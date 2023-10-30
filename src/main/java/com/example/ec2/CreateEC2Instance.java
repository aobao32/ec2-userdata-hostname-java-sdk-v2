package com.example.ec2;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.InstanceType;
import software.amazon.awssdk.services.ec2.model.RunInstancesRequest;
import software.amazon.awssdk.services.ec2.model.RunInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.VolumeType;
import software.amazon.awssdk.services.ec2.model.BlockDeviceMapping;
import software.amazon.awssdk.services.ec2.model.CreateTagsRequest;
import software.amazon.awssdk.services.ec2.model.EbsBlockDevice;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
import java.util.Base64;

public class CreateEC2Instance {
    public static void main(String[] args) {

        final String usage = "\n" +
            "Usage:\n" +
            "   <tag> <amiId>\n\n" +
            "Where:\n" +
            "   tag - Tag of instance with Name value from the AWS Console (for example, mytest01). \n\n" +
            "   amiId - An Amazon Machine Image (AMI) value that you can obtain from the AWS Console (for example, ami-06018068a18569ff2). \n\n" ;

        if (args.length != 2) {
            System.out.println(usage);
            System.exit(1);
        }

        String name = args[0];
        String amiId = args[1];

        Region region = Region.AP_SOUTHEAST_1;
        ProfileCredentialsProvider credentialsProvider = ProfileCredentialsProvider.create();

        Ec2Client ec2 = Ec2Client.builder()
            .region(region)
            .credentialsProvider(credentialsProvider)
            .build();

        String instanceId = createEC2Instance(ec2,name, amiId) ;
        System.out.println("The Amazon EC2 Instance ID is "+instanceId+"\n");
        ec2.close();
    }

    public static String createEC2Instance(Ec2Client ec2, String name, String amiId ) {

        BlockDeviceMapping blockDeviceMapping = BlockDeviceMapping.builder()
            .deviceName("/dev/xvda")
            .ebs(EbsBlockDevice.builder()
                .volumeType(VolumeType.GP3)
                .volumeSize(20)
                .build())
            .build();

        String userdatatext = "#!/bin/bash \n" + 
                    "hostnamectl set-hostname " + name ;
        String userdatabase64 = Base64.getEncoder().encodeToString(userdatatext.getBytes());

        RunInstancesRequest runRequest = RunInstancesRequest.builder()
            .imageId(amiId)
            .instanceType(InstanceType.T3_SMALL)
            .maxCount(1)
            .minCount(1)
            .blockDeviceMappings(blockDeviceMapping)
            .userData(userdatabase64)
            .build();

        RunInstancesResponse response = ec2.runInstances(runRequest);
        String instanceId = response.instances().get(0).instanceId();
        Tag tag = Tag.builder()
            .key("Name")
            .value(name)
            .build();

        CreateTagsRequest tagRequest = CreateTagsRequest.builder()
            .resources(instanceId)
            .tags(tag)
            .build();

        try {
            ec2.createTags(tagRequest);
            System.out.printf(  "\n\nSuccessfully started EC2 Instance %s based on AMI %s", instanceId, amiId);
            System.out.printf("\n\n");
            return instanceId;

        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }

        return "";
    }
}