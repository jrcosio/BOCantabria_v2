package com.jrblanco.boccantabria.fake

import mockwebserver3.MockWebServer
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.rules.ExternalResource
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A `MockWebServer` that speaks TLS, and a client that trusts it.
 *
 * Every network test of this project needs the same seventeen lines: the application only ever talks
 * https — the feed catalogue and the document downloader both refuse anything else — and a test
 * server speaking plain HTTP would be testing something the application does not do. The block was
 * copied into five test classes before feature 014 made it a rule.
 *
 * [calls] records every call the client started, so a test that cancels a coroutine can assert that
 * the underlying `Call` was cancelled too (PERF-002). The data sources derive their clients with
 * `newBuilder()`, which keeps the interceptor.
 */
class TlsMockWebServer : ExternalResource() {

    lateinit var server: MockWebServer
        private set

    lateinit var client: OkHttpClient
        private set

    val calls: MutableList<Call> = CopyOnWriteArrayList()

    override fun before() {
        val localhost = InetAddress.getByName("localhost").canonicalHostName
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName(localhost).build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()

        server = MockWebServer()
        server.useHttps(serverCertificates.sslSocketFactory())
        server.start()

        client = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .retryOnConnectionFailure(false)
            .addInterceptor { chain ->
                calls += chain.call()
                chain.proceed(chain.request())
            }
            .build()
    }

    override fun after() {
        runCatching { server.close() }
    }

    fun url(path: String): String = server.url(path).toString()
}
