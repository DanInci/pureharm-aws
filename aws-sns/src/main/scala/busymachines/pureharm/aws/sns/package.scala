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
package busymachines.pureharm.aws

import busymachines.pureharm.phantom._

/**
  *
  * @author Lorand Szakacs, https://github.com/lorandszakacs
  * @since 19 Nov 2019
  *
  */
package object sns {

  object SNSDeviceToken extends PhantomType[String]

  /**
    * @see
    *   Section "Token"
    *   https://docs.aws.amazon.com/sns/latest/api/API_CreatePlatformEndpoint.html
    */
  type SNSDeviceToken = SNSDeviceToken.Type

  object SNSEndpointARN extends PhantomType[String]
  type SNSEndpointARN = SNSEndpointARN.Type

  object SNSPlatformApplicationARN extends PhantomType[String]
  type SNSPlatformApplicationARN = SNSPlatformApplicationARN.Type

  object SNSAccessKeyID extends PhantomType[String]

  /**
    * Standard AWS AccessKeyID, of different type, so it's harder
    * to accidentally mix up with the AccessKey used for S3,
    * in case they have different values. Since these are read
    * from a config, they can easily be set to have the same value,
    * but our code ought to be agnostic of this knowledge.
    */
  type SNSAccessKeyID = SNSAccessKeyID.Type

  object SNSSecretAccessKey extends PhantomType[String]

  /**
    * Standard AWS SecretAccessKey, same reasoning as behind
    * [[SNSAccessKeyID]]
    */
  type SNSSecretAccessKey = SNSSecretAccessKey.Type
}
