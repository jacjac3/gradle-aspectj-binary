package com.github.sedovalx.sandbox.gradle.aspectj.examples.aspects

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class KotlinAnnotation(val value: String)
