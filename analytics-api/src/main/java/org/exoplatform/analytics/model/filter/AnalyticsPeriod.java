/**
 * This file is part of the Meeds project (https://meeds.io/).
 * Copyright (C) 2022 Meeds Association
 * contact@meeds.io
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.exoplatform.analytics.model.filter;

import java.io.Serializable;
import java.time.*;
import java.time.temporal.ChronoUnit;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsPeriod implements Serializable, Cloneable {

  private static final long serialVersionUID = 2730342636949170231L;

  private long              fromInMS;

  private long              toInMS;

  private String            interval;

  private ZoneId            timeZone         = ZoneOffset.UTC;

  public AnalyticsPeriod(long fromInMS, long toInMS) {
    this.fromInMS = fromInMS;
    this.toInMS = toInMS;
    this.interval = getDiffInDays() + "d";
  }

  public AnalyticsPeriod(long fromInMS, long toInMS, ZoneId timeZone) {
    this(fromInMS, toInMS);
    this.timeZone = timeZone;
  }

  public AnalyticsPeriod(LocalDate from, LocalDate to) {
    this.fromInMS = from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    this.toInMS = to.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    this.interval = getDiffInDays() + "d";
  }

  public AnalyticsPeriod(LocalDate from, LocalDate to, String interval) {
    this.fromInMS = from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    this.toInMS = to.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    this.interval = interval;
  }

  public AnalyticsPeriod(LocalDate from, LocalDate to, String interval, ZoneId timeZone) {
    this.fromInMS = from.atStartOfDay(timeZone).toInstant().toEpochMilli();
    this.toInMS = to.atStartOfDay(timeZone).toInstant().toEpochMilli();
    this.interval = interval;
    this.timeZone = timeZone;
  }

  public LocalDate getFrom() {
    return Instant.ofEpochMilli(fromInMS).atZone(getTimeZone()).toLocalDate();
  }

  public LocalDate getTo() {
    return Instant.ofEpochMilli(toInMS).atZone(getTimeZone()).toLocalDate();
  }

  public AnalyticsPeriod previousPeriod() {
    return new AnalyticsPeriod(fromInMS - (toInMS - fromInMS), fromInMS);
  }

  public long getDiffInDays() {
    return ChronoUnit.DAYS.between(getFrom(), getTo());
  }

  public boolean isInPeriod(long timestamp) {
    return timestamp >= getFromInMS() && timestamp < getToInMS();
  }

  public boolean isInPeriod(LocalDate date) {
    long dateInMS = date.atStartOfDay(getTimeZone()).toInstant().toEpochMilli();
    return dateInMS >= fromInMS && dateInMS < toInMS;
  }

  @Override
  protected AnalyticsPeriod clone() {// NOSONAR
    return new AnalyticsPeriod(fromInMS, toInMS, interval, timeZone);
  }

}
