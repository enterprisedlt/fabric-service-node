package org.enterprisedlt.fabric.service.node.model

import com.google.common.reflect.ClassPath
import org.enterprisedlt.general.gson.TypeNameResolver
import scala.collection.JavaConverters._
/**
 * @author Andrew Pudovikov
 */

object ServiceNodeTypeNameResolver extends TypeNameResolver {
    private val types: Iterable[Class[_]] = scanPackage("org.enterprisedlt.fabric.service.node.model")

    private val typeByName = types.map(clz => (resolveNameByType(clz), clz)).toMap[String, Class[_]]

    override def resolveTypeByName(name: String): Class[_] = typeByName.getOrElse(name, throw new RuntimeException(s"Unknown type name $name"))

    override def resolveNameByType(clazz: Class[_]): String = clazz.getSimpleName

    private def scanPackage(pName: String): Iterable[Class[_]] =
        ClassPath.from(Thread.currentThread.getContextClassLoader)
          .getAllClasses
          .asScala
          .filter { clzInfo =>
              clzInfo.getPackageName.startsWith(pName)
          }
          .map { clzInfo =>
              clzInfo.load()
          }

}
