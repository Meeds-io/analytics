<%
/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2024 Meeds Association contact@meeds.io
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
%>
<%@ taglib uri="http://java.sun.com/portlet_2_0" prefix="portlet" %>

<%@ page import="org.exoplatform.portal.config.model.Page"%>
<%@ page import="org.exoplatform.portal.application.PortalRequestContext"%>
<%@ page import="org.exoplatform.portal.config.UserACL"%>
<%@ page import="org.exoplatform.container.ExoContainerContext"%>
<%@ page import="org.exoplatform.social.core.space.SpacesAdministrationService"%>
<%@ page import="org.exoplatform.social.webui.Utils"%>
<%@ page import="org.exoplatform.social.core.identity.model.Identity"%>

<portlet:defineObjects />
<portlet:resourceURL var="resourceURL" />

<%
  String portletStorageId = ((String) request.getAttribute("portletStorageId"));
  String spacesRecentlyVisitedLimit = request.getAttribute("spacesRecentlyVisitedLimit") == null ? "4" : ((String[]) request.getAttribute("spacesRecentlyVisitedLimit"))[0];
  String spacesMostActiveLimit = request.getAttribute("spacesMostActiveLimit") == null ? "0" : ((String[]) request.getAttribute("spacesMostActiveLimit"))[0];
  Page currentPage = PortalRequestContext.getCurrentInstance().getPage();
  boolean canEdit = ExoContainerContext.getService(UserACL.class)
      .hasEditPermission(currentPage);
  String pageRef = currentPage.getPageKey().format();
  boolean canCreateSpace = ExoContainerContext.getService(SpacesAdministrationService.class)
      .canCreateSpace(request.getRemoteUser());
  Identity viewerIdentity = Utils.getViewerIdentity();
  boolean isExternal = viewerIdentity == null || viewerIdentity.isExternal();
%>
<div class="VuetifyApp">
  <div id="spacesListWidget">
    <script type="text/javascript">
      window.require(['PORTLET/analytics/SpacesListWidget'], app => app.init(
        '<%=resourceURL%>',
        <%=portletStorageId%>,
        <%=spacesRecentlyVisitedLimit%>,
        <%=spacesMostActiveLimit%>,
        <%=canEdit%>,
        '<%=pageRef%>',
        <%=canCreateSpace%>,
        <%=isExternal%>
      ));
    </script>
  </div>
</div>
