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
package busymachines.pureharm.aws.s3.internals

import busymachines.pureharm.aws.s3._
import busymachines.pureharm.effects._
import busymachines.pureharm.effects.implicits._

/**
  *
  * @author Lorand Szakacs, https://github.com/lorandszakacs
  * @since 10 Jul 2019
  *
  */
private[s3] object ImpureJavaS3 {

  import software.amazon.awssdk.core.internal.util.Mimetype
  import software.amazon.awssdk.services.s3.S3AsyncClient
  import software.amazon.awssdk.core.ResponseBytes
  import software.amazon.awssdk.core.async._
  import software.amazon.awssdk.services.s3.model._

  def put[F[_]](
    client: S3AsyncClient,
  )(
    bucket:  S3Bucket,
    key:     S3FileKey,
    content: S3BinaryContent,
  )(
    implicit
    F: Async[F],
  ): F[S3FileKey] = {

    for {
      jpath <- S3FileKey.asJPath(key).liftTo[F]
      mimeType = Mimetype.getInstance().getMimetype(jpath)
      putRequest = PutObjectRequest
        .builder()
        .bucket(bucket)
        .key(key)
        .contentType(mimeType)
        .build()

      reqBody <- Sync[F].delay(AsyncRequestBody.fromBytes(content))
      ff: F[Interop.JCFuture[PutObjectResponse]] = Sync[F].delay(
        client.putObject(putRequest, reqBody),
      )
      _ <- Interop.toF(ff)
    } yield key

  }

  def get[F[_]: Async](client: S3AsyncClient)(
    bucket: S3Bucket,
    key:    S3FileKey,
  ): F[S3BinaryContent] = {
    for {
      transformer <- asyncBytesTransformer[F]
      getReq      <- GetObjectRequest.builder().bucket(bucket).key(key).bucket(bucket).build().pure[F]
      content     <- Interop.toF(Sync[F].delay(client.getObject(getReq, transformer)))
    } yield content
  }

  def delete[F[_]: Async](client: S3AsyncClient)(
    bucket: S3Bucket,
    key:    S3FileKey,
  ): F[Unit] = {
    for {
      delReq <- DeleteObjectRequest.builder().bucket(bucket).key(key).bucket(bucket).build().pure[F]
      _      <- Interop.toF(Sync[F].delay(client.deleteObject(delReq)))
    } yield ()
  }

  def list[F[_]: Async](client: S3AsyncClient)(
    bucket: S3Bucket,
    prefix: S3Path,
  ): F[List[S3FileKey]] = {
    import scala.jdk.CollectionConverters._
    for {
      listReq <- ListObjectsRequest.builder().bucket(bucket).prefix(prefix).build().pure[F]
      keys <- Interop
        .toF(Sync[F].delay(client.listObjects(listReq)))
        .map(_.contents().asScala.toList.map(obj => S3FileKey.unsafe(obj.key())))
    } yield keys
  }

  def exists[F[_]: Async](client: S3AsyncClient)(
    bucket: S3Bucket,
    key:    S3FileKey,
  ): F[Boolean] =
    for {
      headReq <- HeadObjectRequest.builder().bucket(bucket).key(key).build().pure[F]
      exists <- Interop.toF(Sync[F].delay(client.headObject(headReq))).map(_ => true).recover {
        case _: S3Exception => false
      }
    } yield exists

  def copy[F[_]: Async](client: S3AsyncClient)(
    fromBucket: S3Bucket,
    fromKey:    S3FileKey,
    toBucket:   S3Bucket,
    toKey:      S3FileKey,
  ): F[Unit] = {
    import java.net.URLEncoder
    import java.nio.charset.StandardCharsets
    for {
      urlEncodedSource <- Sync[F].delay(URLEncoder.encode(s"$fromBucket/$fromKey", StandardCharsets.UTF_8.toString))
      copyReq <- CopyObjectRequest
        .builder()
        .copySource(urlEncodedSource)
        .destinationBucket(toBucket)
        .destinationKey(toKey)
        .build()
        .pure[F]
      _ <- Interop.toF(Sync[F].delay(client.copyObject(copyReq)))
    } yield ()
  }

  private def asyncBytesTransformer[F[_]: Sync]: F[AsyncResponseTransformer[GetObjectResponse, S3BinaryContent]] =
    for {
      bf <- Sync[F].delay(AsyncResponseTransformer.toBytes[GetObjectResponse])
    } yield new AsyncBytesTransformer(bf)

  private class AsyncBytesTransformer(
    private val impl: AsyncResponseTransformer[GetObjectResponse, ResponseBytes[GetObjectResponse]],
  ) extends AsyncResponseTransformer[GetObjectResponse, S3BinaryContent] {
    import java.nio.ByteBuffer
    import java.util.concurrent.CompletableFuture

    override def prepare(): CompletableFuture[S3BinaryContent] = {
      impl.prepare().thenApply(cf => S3BinaryContent(cf.asByteArray()))
    }

    override def onResponse(response: GetObjectResponse): Unit = impl.onResponse(response)

    override def onStream(publisher: SdkPublisher[ByteBuffer]): Unit = impl.onStream(publisher)

    override def exceptionOccurred(error: Throwable): Unit = impl.exceptionOccurred(error)
  }
}
