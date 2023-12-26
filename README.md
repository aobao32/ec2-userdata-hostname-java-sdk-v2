# 使用AWS Java SDK V2创建EC2、并通过Userdata指定Hostname

本文介绍了如果通过Userdata在首次创建EC2时候自定义主机名Hostname。本文使用Java语言，通过AWSSDK的方式调用API发起操作。本文提供了代码和POM参考，在Amazon Linux 2的EC2上编译、测试通过。

## 一、背景

### 1、原理

在通过API创建EC2时候，需要提供几个基础参数，包括机型、AMI ID参数是必须显式指定的。其他参数是可选，如果不指定，AWS会使用默认的VPC、随机分配子网、默认的安全组等参数进行EC2创建。在这些参数中，并没有名为`Hostname`主机名的参数。因此如果是需要创建EC2时候指定Hostname，直接传参数是不行的。

虽然如此，本问题有很简单的Workaround，那就是将Hostname放在EC2的Userdata参数中传递过去，即可实现。原理是在创建EC2后自动，Userdata脚本会在EC2上自动执行，此时可进行修改本机默认密码、修改Hostname、修改DNS、修改SSH配置、安装软件包、拉取特定软件源、乃至安全加固等操作。因此，使用Userdata方式来修改主机名是易用便捷的。

### 2、通过AWS控制台配置

如果是在AWS控制台上创建EC2，那么Userdata脚本配置时候可以如下。

```shell
#!/bin/bash
hostnamectl set-hostname yourhostname
```

### 3、通过API发起操作

传输Userdata和使用的语言无关，任何一种语言都可以调用SDK以快速使用AWS API。完全不调用任何SDK，而是靠拼接HTTPS头Post到AWS的API Endpoint也是可以工作的。

通过AWSCLI的Shell方式和Python Boto3 SDK来设置Userdata修改主机名，请参考[这篇文档](https://blog.bitipcman.com/create-ec2-with-userdata-to-modify-hostname-on-awscli-and-python/)。

本文使用SDK以Java语言为例，在Userdata中设置主机名的关键代码片段是：

```java
        RunInstancesRequest runRequest = RunInstancesRequest.builder()
            .imageId(amiId)
            .instanceType(InstanceType.T3_SMALL)
            .maxCount(1)
            .minCount(1)
            .userData(userdatabase64)
            .build();
```
注意事项：

- 不同的开发语言的SDK，对传入Userdata要求不一样，例如AWSCLI可直接写文本。Java SDK V2要求事先Base64编码再传入
- 创建EC2使用的AMI必须能够正常启动和联网，因为Userdata是要在EC2启动后通过Metadata获取Userdata，如果本AMI镜像启动后是没有网卡的镜像，那么Userdata也无法执行
- Userdata必须为两行，第一行指定Shell脚本后，必须包含一个换行符。

本文介绍使用AWS Java SDK V2创建EC2、并通过Userdata指定Hostname。

## 二、获取代码和编译

### 1、主要代码

```shell
git clone https://github.com/aobao32/ec2-userdata-hostname-java-sdk-v2.git
cd ec2-userdata-hostname-java-sdk-v2
```

主要代码（即查看Github中的`src\main\java\com\example\ec2\`目录中的`CreateEC2Instance.java`文件）如下：

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

如果需要指定VPC、指定子网、指定安全组、或挂载第二个磁盘，请参考文档自行调整代码。

### 2、编译需要的POM文件

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>EC2J2Project</groupId>
    <artifactId>EC2J2Project</artifactId>
    <version>1.0-SNAPSHOT</version>
    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <java.version>1.8</java.version>
    </properties>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>2.22.1</version>
                <configuration>
                    <groups>IntegrationTest</groups>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.1</version>
                <configuration>
                    <source>${java.version}</source>
                    <target>${java.version}</target>
                </configuration>
            </plugin>
            <plugin>
		       <groupId>org.apache.maven.plugins</groupId>
		         <artifactId>maven-shade-plugin</artifactId>
			     <version>3.0.0</version>
			        <executions>
					   <execution>
						<phase>package</phase>
						  <goals>
						    <goal>shade</goal>
						  </goals>
						</execution>
					</executions>
		    </plugin>
            <plugin>
              <groupId>org.apache.maven.plugins</groupId>
              <artifactId>maven-jar-plugin</artifactId>
              <version>3.1.0</version>
              <configuration>
                <archive>
                  <manifest>
                    <addClasspath>true</addClasspath>
                    <classpathPrefix>lib/</classpathPrefix>
                    <mainClass>com.example.ec2.CreateEC2Instance</mainClass>
                  </manifest>
                </archive>
              </configuration>
            </plugin>
        </plugins>
    </build>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>software.amazon.awssdk</groupId>
                <artifactId>bom</artifactId>
                <version>2.20.45</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-api</artifactId>
            <version>5.9.2</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-engine</artifactId>
            <version>5.9.2</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.platform</groupId>
            <artifactId>junit-platform-commons</artifactId>
            <version>1.9.2</version>
        </dependency>
        <dependency>
            <groupId>com.google.code.gson</groupId>
            <artifactId>gson</artifactId>
            <version>2.10.1</version>
        </dependency>
        <dependency>
            <groupId>org.junit.platform</groupId>
            <artifactId>junit-platform-launcher</artifactId>
            <version>1.9.2</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>ec2</artifactId>
        </dependency>
       <dependency>
           <groupId>org.slf4j</groupId>
           <artifactId>slf4j-api</artifactId>
           <version>2.0.9</version>
       </dependency>
       <dependency>
           <groupId>org.slf4j</groupId>
           <artifactId>slf4j-simple</artifactId>
           <version>2.0.9</version>
       </dependency>
    </dependencies>
</project>
```

### 3、编译和运行

返回到`pom.xml`文件所在的代码根目录。编译并运行程序。编译后target目录下获得Jar文件。执行这个文件即可创建EC2。

```
mvn clean install
java -jar target/EC2J2Project-1.0-SNAPSHOT.jar hostname AMI-ID
```

### 4、运行效果

可看到代码正常输出了创建EC2之后的EC2 ID。这个ID也就是AWS控制台上显示的`i-xxxxx`的ID，这个编号是要创建成功后才返回的，创建失败的话不会返回这个ID。

![](https://blogimg.bitipcman.com/workshop/EC2-SDK/ec2-hostname.png)

## 三、参考文档

Amazon EC2 examples using SDK for Java 2.x

[https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/java_ec2_code_examples.html]()

aws-doc-sdk-examples - create isntance

[https://github.com/awsdocs/aws-doc-sdk-examples/blob/main/javav2/example_code/ec2/src/main/java/com/example/ec2/CreateInstance.java]()

使用Userdata的代码样例

[https://github.com/awsdocs/aws-doc-sdk-examples/blob/main/javav2/example_code/ec2/src/main/java/com/example/ec2/CreateInstance.java]()