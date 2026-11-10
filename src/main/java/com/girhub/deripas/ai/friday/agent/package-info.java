/**
 * По умолчанию всё в пакете не-null. Аннотация из JSpecify — тот же диалект,
 * на котором говорят Spring Framework 7 и Spring AI 2, поэтому наши переопределения
 * их методов совпадают по контракту. Исключения помечаются {@code @Nullable} точечно.
 */
@NullMarked @ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.girhub.deripas.ai.friday.agent;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
