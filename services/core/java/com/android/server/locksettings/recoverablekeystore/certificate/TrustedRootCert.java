/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.locksettings.recoverablekeystore.certificate;

import java.security.cert.X509Certificate;

/**
 * Holds the X509 certificate of the trusted root CA cert for the recoverable key store service.
 *
 * TODO: Read the certificate from a PEM file directly and remove this class.
 */
public final class TrustedRootCert {

    private  static final String TRUSTED_ROOT_CERT_BASE64 = ""
            + "MIIFJjCCAw6gAwIBAgIJAIobXsJlzhNdMA0GCSqGSIb3DQEBDQUAMCAxHjAcBgNV"
            + "BAMMFUdvb2dsZSBDcnlwdEF1dGhWYXVsdDAeFw0xODAyMDIxOTM5MTRaFw0zODAx"
            + "MjgxOTM5MTRaMCAxHjAcBgNVBAMMFUdvb2dsZSBDcnlwdEF1dGhWYXVsdDCCAiIw"
            + "DQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIBAK2OT5i40/H7LINg/lq/0G0hR65P"
            + "Q4Mud3OnuVt6UIYV2T18+v6qW1yJd5FcnND/ZKPau4aUAYklqJuSVjOXQD0BjgS2"
            + "98Xa4dSn8Ci1rUR+5tdmrxqbYUdT2ZvJIUMMR6fRoqi+LlAbKECrV+zYQTyLU68w"
            + "V66hQpAButjJKiZzkXjmKLfJ5IWrNEn17XM988rk6qAQn/BYCCQGf3rQuJeksGmA"
            + "N1lJOwNYxmWUyouVwqwZthNEWqTuEyBFMkAT+99PXW7oVDc7oU5cevuihxQWNTYq"
            + "viGB8cck6RW3cmqrDSaJF/E+N0cXFKyYC7FDcggt6k3UrxNKTuySdDEa8+2RTQqU"
            + "Y9npxBlQE+x9Ig56OI1BG3bSBsGdPgjpyHadZeh2tgk+oqlGsSsum24YxaxuSysT"
            + "Qfcu/XhyfUXavfmGrBOXerTzIl5oBh/F5aHTV85M2tYEG0qsPPvSpZAWtdJ/2rca"
            + "OxvhwOL+leZKr8McjXVR00lBsRuKXX4nTUMwya09CO3QHFPFZtZvqjy2HaMOnVLQ"
            + "I6b6dHEfmsHybzVOe3yPEoFQSU9UhUdmi71kwwoanPD3j9fJHmXTx4PzYYBRf1ZE"
            + "o+uPgMPk7CDKQFZLjnR40z1uzu3O8aZ3AKZzP+j7T4XQKJLQLmllKtPgLgNdJyib"
            + "2Glg7QhXH/jBTL6hAgMBAAGjYzBhMB0GA1UdDgQWBBSbZfrqOYH54EJpkdKMZjMc"
            + "z/Hp+DAfBgNVHSMEGDAWgBSbZfrqOYH54EJpkdKMZjMcz/Hp+DAPBgNVHRMBAf8E"
            + "BTADAQH/MA4GA1UdDwEB/wQEAwIBhjANBgkqhkiG9w0BAQ0FAAOCAgEAKh9nm/vW"
            + "glMWp3vcCwWwJW286ecREDlI+CjGh5h+f2N4QRrXd/tKE3qQJWCqGx8sFfIUjmI7"
            + "KYdsC2gyQ2cA2zl0w7pB2QkuqE6zVbnh1D17Hwl19IMyAakFaM9ad4/EoH7oQmqX"
            + "nF/f5QXGZw4kf1HcgKgoCHWXjqR8MqHOcXR8n6WFqxjzJf1jxzi6Yo2dZ7PJbnE6"
            + "+kHIJuiCpiHL75v5g1HM41gT3ddFFSrn88ThNPWItT5Z8WpFjryVzank2Yt02LLl"
            + "WqZg9IC375QULc5B58NMnaiVJIDJQ8zoNgj1yaxqtUMnJX570lotO2OXe4ec9aCQ"
            + "DIJ84YLM/qStFdeZ9416E80dchskbDG04GuVJKlzWjxAQNMRFhyaPUSBTLLg+kwP"
            + "t9+AMmc+A7xjtFQLZ9fBYHOBsndJOmeSQeYeckl+z/1WQf7DdwXn/yijon7mxz4z"
            + "cCczfKwTJTwBh3wR5SQr2vQm7qaXM87qxF8PCAZrdZaw5I80QwkgTj0WTZ2/GdSw"
            + "d3o5SyzzBAjpwtG+4bO/BD9h9wlTsHpT6yWOZs4OYAKU5ykQrncI8OyavMggArh3"
            + "/oM58v0orUWINtIc2hBlka36PhATYQiLf+AiWKnwhCaaHExoYKfQlMtXBodNvOK8"
            + "xqx69x05q/qbHKEcTHrsss630vxrp1niXvA=";

    /**
     * The X509 certificate of the trusted root CA cert for the recoverable key store service.
     *
     * TODO: Change it to the production certificate root CA before the final launch.
     */
    public static final X509Certificate TRUSTED_ROOT_CERT;

    static {
        try {
            TRUSTED_ROOT_CERT = CertUtils.decodeCert(
                    CertUtils.decodeBase64(TRUSTED_ROOT_CERT_BASE64));
        } catch (CertParsingException e) {
            // Should not happen
            throw new RuntimeException(e);
        }
    }
}
