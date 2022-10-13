module org.apache.sis.cloud.aws {
    exports org.apache.sis.cloud.aws.s3;

    requires org.apache.sis.util;
    requires software.amazon.awssdk.services.s3;
}