package org.enterprisedlt.fabric.service.node.rest

import java.io.InputStreamReader
import java.lang.reflect.{InvocationHandler, Method, ParameterizedType, Proxy => JProxy}
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import com.google.gson.{Gson, GsonBuilder}
import org.apache.http.HttpStatus
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.entity.{ByteArrayEntity, ContentType}
import org.apache.http.impl.client.HttpClients
import org.slf4j.LoggerFactory

import scala.reflect.{ClassTag, classTag}

/**
  * @author Alexey Polubelov
  */
object JsonRestClient {
    def create[T: ClassTag](url: String): T = {
        val targetClazz = classTag[T].runtimeClass.asInstanceOf[Class[T]]
        JProxy
          .newProxyInstance(targetClazz.getClassLoader, Array(targetClazz), new ClientProxy(url))
          .asInstanceOf[T]
    }

    private def getCodec: Gson = new GsonBuilder().setPrettyPrinting().create()

    private class ClientProxy(url: String) extends InvocationHandler {
        private val logger = LoggerFactory.getLogger(this.getClass)
        private val client = HttpClients.createDefault()


        override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef = {
            logger.info(s"Executing ${method.getName} ...")
            method.getGenericReturnType match {
                case pt: ParameterizedType =>
                    val pts = pt.getActualTypeArguments
                    val returnType = pts(1).asInstanceOf[Class[_]]
                    if (method.isAnnotationPresent(classOf[Get])) {
                        val path = method.getAnnotation(classOf[Get]).value()
                        val params = method.getParameters.map(_.getName).zip(args.map(getCodec.toJson).map(URLEncoder.encode(_, StandardCharsets.UTF_8.name())))
                        val targetUrl = s"$url$path${params.map(v => s"${v._1}=${v._2}").mkString("?", "&", "")}"
                        logger.debug(s"Target URL is $targetUrl")
                        val request = new HttpGet(targetUrl)
                        val response = client.execute(request)
                        try {
                            logger.info(s"Got status from remote: ${response.getStatusLine.toString}")
                            val entity = response.getEntity
                            response.getStatusLine.getStatusCode match {
                                case HttpStatus.SC_OK =>
                                    println(s"*********\nCONTENT TYPE: ${entity.getContentType}\n*********")
                                    Right(getCodec.fromJson(new InputStreamReader(entity.getContent), returnType))

                                case _ =>
                                    println(s"*********\nCONTENT TYPE: ${entity.getContentType}\n*********")
                                    Left(getCodec.fromJson(new InputStreamReader(entity.getContent), classOf[String]))

                            }
                        } finally {
                            response.close()
                        }
                    }
                    else if (method.isAnnotationPresent(classOf[Post])) {
                        val path = method.getAnnotation(classOf[Post]).value()
                        if(args.length != 1) {
                            throw new Exception("Post method supported only with single argument")
                        }
                        val targetUrl = s"$url$path"
                        val request = new HttpPost(targetUrl)
                        val body = getCodec.toJson(args(0)).getBytes(StandardCharsets.UTF_8)
                        logger.debug(s"Executing POST to $targetUrl")
                        val entity = new ByteArrayEntity(body, ContentType.APPLICATION_JSON)
                        request.setEntity(entity)
                        val response = client.execute(request)
                        try {
                            logger.info(s"Got status from remote: ${response.getStatusLine.toString}")
                            val entity = response.getEntity
                            response.getStatusLine.getStatusCode match {
                                case HttpStatus.SC_OK =>
                                    Right(getCodec.fromJson(new InputStreamReader(entity.getContent), returnType))

                                case _ =>
                                    Left(getCodec.fromJson(new InputStreamReader(entity.getContent), classOf[String]))

                            }
                        } finally {
                            response.close()
                        }
                    } else {
                        throw new Exception("Method does not annotated with Get or Post")
                    }
                case _ =>
                    val msg = s"Not a parametrized return type"
                    logger.error(msg)
                    throw new Exception(msg)
            }
        }
    }

}
