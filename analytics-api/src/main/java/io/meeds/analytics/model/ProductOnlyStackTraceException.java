/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package io.meeds.analytics.model;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

public class ProductOnlyStackTraceException extends RuntimeException {

  private static final String BASE_PACKAGE_COM_EXOPLATFORM = "com.exoplatform";

  private static final String BASE_PACKAGE_ORG_EXOPLATFORM = "org.exoplatform";

  private static final String BASE_PACKAGE_IO_MEEDS        = "io.meeds";

  private static final long   serialVersionUID             = 2090796822546017069L;

  private boolean             initialized;                                        // NOSONAR

  @Override
  public StackTraceElement[] getStackTrace() {
    initStackTrace();
    return super.getStackTrace();
  }

  @Override
  public void printStackTrace(PrintStream s) {
    initStackTrace();
    super.printStackTrace(s);
  }

  @Override
  public void printStackTrace(PrintWriter s) {
    initStackTrace();
    super.printStackTrace(s);
  }

  private void initStackTrace() {
    if (initialized) {
      return;
    }
    initialized = true;
    StackTraceElement[] stackTrace = getStackTrace();
    List<StackTraceElement> productStackTrace = Arrays.stream(stackTrace)
                                                      .filter(trace -> StringUtils.containsAny(trace.getClassName(),
                                                                                               BASE_PACKAGE_IO_MEEDS,
                                                                                               BASE_PACKAGE_ORG_EXOPLATFORM,
                                                                                               BASE_PACKAGE_COM_EXOPLATFORM))
                                                      .toList();
    setStackTrace(productStackTrace.toArray(new StackTraceElement[productStackTrace.size()]));
  }

  @Override
  public synchronized Throwable initCause(Throwable cause) {
    StackTraceElement[] stackTrace = getStackTrace();
    List<StackTraceElement> productStackTrace = Arrays.stream(stackTrace)
                                                      .filter(trace -> StringUtils.containsAny(trace.getClassName(),
                                                                                               BASE_PACKAGE_IO_MEEDS,
                                                                                               BASE_PACKAGE_ORG_EXOPLATFORM,
                                                                                               BASE_PACKAGE_COM_EXOPLATFORM))
                                                      .toList();
    cause.setStackTrace(productStackTrace.toArray(new StackTraceElement[productStackTrace.size()]));
    return super.initCause(cause);
  }

  @Override
  public synchronized Throwable getCause() {
    Throwable cause = super.getCause();
    StackTraceElement[] stackTrace = getStackTrace();
    List<StackTraceElement> productStackTrace = Arrays.stream(stackTrace)
                                                      .filter(trace -> StringUtils.containsAny(trace.getClassName(),
                                                                                               BASE_PACKAGE_IO_MEEDS,
                                                                                               BASE_PACKAGE_ORG_EXOPLATFORM,
                                                                                               BASE_PACKAGE_COM_EXOPLATFORM))
                                                      .toList();
    cause.setStackTrace(productStackTrace.toArray(new StackTraceElement[productStackTrace.size()]));
    return cause;
  }
}
