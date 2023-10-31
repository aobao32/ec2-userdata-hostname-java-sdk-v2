# 使用AWS Java SDK V2创建EC2、并通过Userdata指定Hostname

## 一、背景

在通过API创建EC2时候，需要提供几个基础参数，包括机型、AMI ID参数是必须显式指定的。其他参数是可选，如果不指定，AWS会使用默认的VPC、随机分配子网、默认的安全组等参数进行EC2创建。在这些参数中，没有名为`Hostname`主机名的参数。不过，由于AWS的API支持传入Userdata，可以在创建EC2后自动在EC2上执行脚本，因此，指定主机名的功能可通过在创建EC2的请求时候加上一段Userdata脚本解决。脚本如下。

```
#!/bin/bash
hostnamectl set-hostname yourhostname
```

以Java语言为例，将Userdata片段合并到API中就是：

```java
        RunInstancesRequest runRequest = RunInstancesRequest.builder()
            .imageId(amiId)
            .instanceType(InstanceType.T3_SMALL)
            .maxCount(1)
            .minCount(1)
            .userData(userdatabase64)
            .build();
```

传输Userdata和使用的语言无关，任何一种语言都可以调用SDK以快速使用AWS API。完全不适用任何SDK，而是靠拼接HTTPS头Post到AWS的API Endpoint也是可以工作的。[这篇文档](https://blog.bitipcman.com/create-ec2-with-userdata-to-modify-hostname-on-awscli-and-python/)介绍了使用AWSCLI的Shell脚本和Python3的boto3 SDK来完成这一工作。

注意事项：

- 不同的开发语言的SDK，对传入Userdata要求不一样，例如AWSCLI可直接写文本。Java SDK V2要求事先Base64编码再传入
- 创建EC2使用的AMI必须能够正常启动和联网，因为Userdata是要在EC2启动后通过Metadata获取Userdata，如果本AMI镜像启动后是没有网卡的镜像，那么Userdata也无法执行
- Userdata必须为两行，第一行指定Shell脚本后，必须包含一个换行符。

本文介绍使用AWS Java SDK V2创建EC2、并通过Userdata指定Hostname。

## 二、环境准备

```
git clone https://github.com/aobao32/ec2-userdata-hostname-java-sdk-v2.git
cd ec2-userdata-hostname-java-sdk-v2
mvn clean install
```

运行程序

```
java -jar target/EC2J2Project-1.0-SNAPSHOT.jar hostname AMI-ID
```

即可创建EC2。

如果需要指定VPC、指定子网、指定安全组、或挂载第二个磁盘，请参考文档自行调整代码。

## 二、代码

```java
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
```

## 三、运行效果

![](https://blogimg.bitipcman.com/workshop/EC2-SDK/ec2-hostname.png)

## 四、参考文档

Amazon EC2 examples using SDK for Java 2.x

[https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/java_ec2_code_examples.html]()

aws-doc-sdk-examples - create isntance

[https://github.com/awsdocs/aws-doc-sdk-examples/blob/main/javav2/example_code/ec2/src/main/java/com/example/ec2/CreateInstance.java]()

Userdata

[https://github.com/awsdocs/aws-doc-sdk-examples/blob/main/javav2/example_code/ec2/src/main/java/com/example/ec2/CreateInstance.java]()