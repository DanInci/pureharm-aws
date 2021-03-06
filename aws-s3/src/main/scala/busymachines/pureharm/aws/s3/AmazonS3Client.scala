/**
  * Copyright (c) 2017-2019 BusyMachines
  *
  * See company homepage at: https://www.busymachines.com/
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *     http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */
package busymachines.pureharm.aws.s3

/**
  *
  * @author Lorand Szakacs, https://github.com/lorandszakacs
  * @since 10 Jul 2019
  *
  */
trait AmazonS3Client[F[_]] {

  def put(bucket: S3Bucket, key: S3FileKey, content: S3BinaryContent): F[Unit]

  def get(bucket: S3Bucket, key: S3FileKey): F[S3BinaryContent]

  def delete(bucket: S3Bucket, key: S3FileKey): F[Unit]

  def list(bucket: S3Bucket, prefix: S3Path): F[List[S3FileKey]]

  def exists(bucket: S3Bucket, key: S3FileKey): F[Boolean]

  def copy(fromBucket: S3Bucket, fromKey: S3FileKey, toBucket: S3Bucket, toKey: S3FileKey): F[Unit]

  def downloadURL(bucket: S3Bucket, key: S3FileKey): F[S3DownloadURL]

}

object AmazonS3Client {
  import busymachines.pureharm.aws.core._
  import busymachines.pureharm.effects._
  import busymachines.pureharm.effects.implicits._

  def resource[F[_]: BlockingShifter](
    config:     S3Config,
  )(implicit F: Async[F]): Resource[F, AmazonS3Client[F]] = {
    for {
      s3Client <- Resource.make(buildSDKClient(config).pure[F]) { c =>
        F.delay(c.close())
      }
    } yield new AmazonS3ClientImpl(s3Client, config)
  }

  def resourceWithFixedBucket[F[_]: BlockingShifter](
    config:     S3Config,
  )(implicit F: Async[F]): Resource[F, AmazonS3ClientForBucket[F]] = {
    for {
      s3Client <- Resource.make(buildSDKClient(config).pure[F]) { c =>
        F.delay(c.close())
      }
    } yield this.withFixedBucket[F](config.bucket, new AmazonS3ClientImpl[F](s3Client, config))
  }

  /**
    * Please use [[resource]], there's no reasonable way to close the
    * underlying S3Client
    */
  def unsafe[F[_]: Async: BlockingShifter](config: S3Config): AmazonS3Client[F] =
    new AmazonS3ClientImpl(buildSDKClient(config), config)

  /**
    * Please use [[resource]], there's no reasonable way to close the
    * underlying S3Client
    */
  def unsafeWithFixedBucket[F[_]: Async: BlockingShifter](config: S3Config): AmazonS3ClientForBucket[F] =
    this.withFixedBucket(config.bucket, this.unsafe[F](config))

  def withFixedBucket[F[_]](bucket: S3Bucket, client: AmazonS3Client[F]): AmazonS3ClientForBucket[F] =
    new AmazonS3ClientForBucketImpl(bucket, client)

  def downloadURL(region: AmazonRegion, bucket: S3Bucket, key: S3FileKey): S3DownloadURL =
    S3DownloadURL(s"https://$bucket.s3.$region.amazonaws.com/$key")

  import software.amazon.awssdk.services.s3.S3AsyncClient

  private def buildSDKClient(config: S3Config): S3AsyncClient = {
    internals.PureJavaS3.buildClient(config)
  }

  private class AmazonS3ClientImpl[F[_]](
    private val s3Client: S3AsyncClient,
    private val config:   S3Config,
  )(
    implicit private val F:       Async[F],
    implicit private val shifter: BlockingShifter[F],
  ) extends AmazonS3Client[F] {

    override def put(bucket: S3Bucket, key: S3FileKey, content: S3BinaryContent): F[Unit] =
      shifter.blockOn(internals.ImpureJavaS3.put(s3Client)(bucket, key, content).void)

    override def get(bucket: S3Bucket, key: S3FileKey): F[S3BinaryContent] =
      shifter.blockOn(internals.ImpureJavaS3.get(s3Client)(bucket, key))

    override def downloadURL(bucket: S3Bucket, key: S3FileKey): F[S3DownloadURL] =
      AmazonS3Client.downloadURL(config.region, bucket, key).pure[F]

    override def delete(bucket: S3Bucket, key: S3FileKey): F[Unit] =
      shifter.blockOn(internals.ImpureJavaS3.delete(s3Client)(bucket, key))

    override def list(bucket: S3Bucket, prefix: S3Path): F[List[S3FileKey]] =
      shifter.blockOn(internals.ImpureJavaS3.list(s3Client)(bucket, prefix))

    override def exists(bucket: S3Bucket, key: S3FileKey): F[Boolean] =
      shifter.blockOn(internals.ImpureJavaS3.exists(s3Client)(bucket, key))

    override def copy(fromBucket: S3Bucket, fromKey: S3FileKey, toBucket: S3Bucket, toKey: S3FileKey): F[Unit] =
      shifter.blockOn(internals.ImpureJavaS3.copy(s3Client)(fromBucket, fromKey, toBucket, toKey))
  }

  private class AmazonS3ClientForBucketImpl[F[_]](
    override val bucket: S3Bucket,
    private val client:  AmazonS3Client[F],
  ) extends AmazonS3ClientForBucket[F] {

    override def put(key: S3FileKey, content: S3BinaryContent): F[Unit] =
      client.put(bucket, key, content)

    override def get(key: S3FileKey): F[S3BinaryContent] =
      client.get(bucket, key)

    override def delete(key: S3FileKey): F[Unit] =
      client.delete(bucket, key)

    override def downloadURL(key: S3FileKey): F[S3DownloadURL] =
      client.downloadURL(bucket, key)

    override def list(prefix: S3Path): F[List[S3FileKey]] =
      client.list(bucket, prefix)

    override def exists(key: S3FileKey): F[Boolean] =
      client.exists(bucket, key)

    override def copy(fromKey: S3FileKey, toKey: S3FileKey): F[Unit] =
      client.copy(bucket, fromKey, bucket, toKey)

    override def copy(fromKey: S3FileKey, toBucket: S3Bucket, toKey: S3FileKey): F[Unit] =
      client.copy(bucket, fromKey, toBucket, toKey)

  }
}
