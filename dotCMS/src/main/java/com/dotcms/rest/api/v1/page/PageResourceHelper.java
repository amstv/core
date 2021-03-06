package com.dotcms.rest.api.v1.page;

import com.dotcms.api.web.HttpServletRequestThreadLocal;
import com.dotcms.business.CloseDB;
import com.dotcms.rendering.velocity.viewtools.DotTemplateTool;
import com.dotcms.rest.exception.BadRequestException;
import com.dotcms.rest.exception.NotFoundException;

import com.dotcms.uuid.shorty.ShortyId;
import com.dotmarketing.beans.ContainerStructure;
import com.dotmarketing.beans.Host;
import com.dotmarketing.beans.MultiTree;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.PermissionLevel;
import com.dotmarketing.business.VersionableAPI;
import com.dotmarketing.business.web.HostWebAPI;
import com.dotmarketing.business.web.WebAPILocator;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.exception.DotRuntimeException;
import com.dotmarketing.exception.DotSecurityException;
import com.dotmarketing.factories.MultiTreeAPI;
import com.dotmarketing.portlets.containers.business.ContainerAPI;
import com.dotmarketing.portlets.containers.model.Container;
import com.dotmarketing.portlets.contentlet.business.ContentletAPI;
import com.dotmarketing.portlets.contentlet.business.HostAPI;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.portlets.htmlpageasset.business.HTMLPageAssetAPI;
import com.dotmarketing.portlets.htmlpageasset.model.HTMLPageAsset;
import com.dotmarketing.portlets.languagesmanager.business.LanguageAPI;
import com.dotmarketing.portlets.languagesmanager.model.Language;
import com.dotmarketing.portlets.templates.business.TemplateAPI;
import com.dotmarketing.portlets.templates.design.bean.TemplateLayout;
import com.dotmarketing.portlets.templates.model.Template;
import com.dotmarketing.util.PageMode;
import com.dotmarketing.util.URLUtils;
import com.dotmarketing.util.UtilMethods;
import com.dotmarketing.util.VelocityUtil;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.velocity.context.Context;
import org.apache.velocity.exception.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.liferay.portal.PortalException;
import com.liferay.portal.SystemException;
import com.liferay.portal.model.User;


/**
 * Provides the utility methods that interact with HTML Pages in dotCMS. These methods are used by
 * the Page REST end-point.
 *
 * @author Will Ezell
 * @author Jose Castro
 * @version 4.2
 * @since Oct 6, 2017
 */
public class PageResourceHelper implements Serializable {

    private static final long serialVersionUID = 296763857542258211L;

    private final HostWebAPI hostWebAPI = WebAPILocator.getHostWebAPI();
    private final HTMLPageAssetAPI htmlPageAssetAPI = APILocator.getHTMLPageAssetAPI();
    private final LanguageAPI languageAPI = APILocator.getLanguageAPI();
    private final VersionableAPI versionableAPI = APILocator.getVersionableAPI();
    private final TemplateAPI templateAPI = APILocator.getTemplateAPI();
    private final ContainerAPI containerAPI = APILocator.getContainerAPI();
    private final ContentletAPI contentletAPI = APILocator.getContentletAPI();
    private final HostAPI hostAPI = APILocator.getHostAPI();
    private final LanguageAPI langAPI = APILocator.getLanguageAPI();
    private final MultiTreeAPI multiTreeAPI = APILocator.getMultiTreeAPI();

    private static final boolean RESPECT_FE_ROLES = Boolean.TRUE;

    /**
     * Private constructor
     */
    private PageResourceHelper() {

    }

    public void saveContent(final String pageId, final List<PageContainerForm.ContainerEntry> containerEntries) throws DotDataException {
        final List<MultiTree> multiTres = new ArrayList<>();

        for (final PageContainerForm.ContainerEntry containerEntry : containerEntries) {
            int i = 0;
            final  List<String> contentIds = containerEntry.getContentIds();

            for (final String contentletId : contentIds) {
                final MultiTree multiTree = new MultiTree().setContainer(containerEntry.getContainerId())
                        .setContentlet(contentletId)
                        .setRelationType(containerEntry.getContainerUUID())
                        .setTreeOrder(i++)
                        .setHtmlPage(pageId);

                multiTres.add(multiTree);
            }
        }

        multiTreeAPI.saveMultiTrees(pageId, multiTres);
    }

    public void saveMultiTree(final String containerId,
                              final String contentletId,
                              final int order,
                              final String uid,
                              final Contentlet page) throws DotDataException {

        final MultiTree multiTree = new MultiTree().setContainer(containerId)
                .setContentlet(contentletId)
                .setRelationType(uid)
                .setTreeOrder(order)
                .setHtmlPage(page.getIdentifier());

        multiTreeAPI.saveMultiTree(multiTree);
    }

    /**
     * Provides a singleton instance of the {@link PageResourceHelper}
     */
    private static class SingletonHolder {
        private static final PageResourceHelper INSTANCE = new PageResourceHelper();
    }

    /**
     * Returns a singleton instance of this class.
     *
     * @return A single instance of this class.
     */
    public static PageResourceHelper getInstance() {
        return PageResourceHelper.SingletonHolder.INSTANCE;
    }

    /**
     * Returns the metadata of an HTML Page in the system and its associated data structures.
     *
     * @param request  The {@link HttpServletRequest} object.
     * @param response The {@link HttpServletResponse} object.
     * @param user     The {@link User} performing this action.
     * @param uri      The path to the HTML Page whose information will be retrieved.
     * @return The {@link PageView} object containing the metadata of the different objects that
     * make up an HTML Page.
     * @throws DotSecurityException The user does not have the specified permissions to perform
     *                              this action.
     * @throws DotDataException     An error occurred when accessing the data source.
     */
    public PageView getPageMetadata(final HttpServletRequest request, final HttpServletResponse
            response, final User user, final String uri, boolean live) throws DotSecurityException,
            DotDataException {
        
        return getPageMetadata(request, response, user, uri, false, PageMode.get(request));
    }

    /**
     * Returns the rendered version of an HTML Page, i.e., the HTML code that will be rendered in
     * the browser.
     *
     * @param request  The {@link HttpServletRequest} object.
     * @param response The {@link HttpServletResponse} object.
     * @param user     The {@link User} performing this action.
     * @param uri      The path to the HTML Page whose information will be retrieved.
     * @return The {@link PageView} object containing the metadata of the different objects that
     * make up an HTML Page.
     * @throws DotSecurityException The user does not have the specified permissions to perform
     *                              this action.
     * @throws DotDataException     An error occurred when accessing the data source.
     */
    public PageView getPageMetadataRendered(final HttpServletRequest request, final
    HttpServletResponse response, final User user, final String uri, boolean live) throws DotSecurityException,
            DotDataException {
        return getPageMetadata(request, response, user, uri, true, PageMode.get(request));
    }

    public String getPageRendered(final HttpServletRequest request, final HttpServletResponse response, final User user,
                                  final String uri, final PageMode mode) throws Exception {

        final HTMLPageAsset page =  this.getPage(request, user, uri, mode);
        return this.getPageRendered(page, request, response, user, mode);
    }

    @CloseDB
    public String getPageRendered(final HTMLPageAsset page, final HttpServletRequest request,
                                  final HttpServletResponse response, final User user, final PageMode mode) throws Exception {

        final String siteName = null == request.getParameter(Host.HOST_VELOCITY_VAR_NAME) ?
                request.getServerName() : request.getParameter(Host.HOST_VELOCITY_VAR_NAME);
        final Host site = this.hostWebAPI.resolveHostName(siteName, user, RESPECT_FE_ROLES);

        if(null==page) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        if(mode.isAdmin ) {
            APILocator.getPermissionAPI().checkPermission(page, PermissionLevel.READ, user);
        }

        return VelocityUtil.eval(mode, request, response, page.getURI(), site);
    }

    public HTMLPageAsset getPage(final HttpServletRequest request, final User user,
                                 final String uri, final PageMode mode) throws DotSecurityException, DotDataException {

        final String siteName = null == request.getParameter(Host.HOST_VELOCITY_VAR_NAME) ?
                request.getServerName() : request.getParameter(Host.HOST_VELOCITY_VAR_NAME);
        final Host site = this.hostWebAPI.resolveHostName(siteName, user, RESPECT_FE_ROLES);

        final String pageUri = URLUtils.addSlashIfNeeded(uri);
        return  (HTMLPageAsset) this.htmlPageAssetAPI.getPageByPath(pageUri,
                site, this.languageAPI.getDefaultLanguage().getId(), mode.respectAnonPerms);
    }

    /**
     * @param request    The {@link HttpServletRequest} object.
     * @param response   The {@link HttpServletResponse} object.
     * @param user       The {@link User} performing this action.
     * @param uri        The path to the HTML Page whose information will be retrieved.
     * @param isRendered If the response must include the final render of the page and its
     *                   containers, set to {@code true}. Otherwise, set to {@code false}.
     * @return The rendered page, i.e., the HTML source code that will be rendered by the browser.
     * @throws DotSecurityException The user does not have the specified permissions to perform
     *                              this action.
     * @throws DotDataException     An error occurred when accessing the data source.
     */
    private PageView getPageMetadata(final HttpServletRequest request, final HttpServletResponse response,
                                     final User user, final String uri, final boolean isRendered, PageMode mode)
            throws DotSecurityException, DotDataException {

        final Context velocityContext = VelocityUtil.getWebContext(request, response);

        final String siteName = null == request.getParameter(Host.HOST_VELOCITY_VAR_NAME) ?
                request.getServerName() : request.getParameter(Host.HOST_VELOCITY_VAR_NAME);
        final Host site = this.hostWebAPI.resolveHostName(siteName, user, RESPECT_FE_ROLES);

        final String pageUri = (uri.length()>0 && '/' == uri.charAt(0)) ? uri : ("/" + uri);
        final HTMLPageAsset page =  (HTMLPageAsset) this.htmlPageAssetAPI.getPageByPath(pageUri,
                site, this.languageAPI.getDefaultLanguage().getId(), mode.showLive);

        final Template template = mode.showLive ? (Template) this.versionableAPI.findLiveVersion(page.getTemplateId(), user, mode.respectAnonPerms) :
                (Template) this.versionableAPI.findWorkingVersion(page.getTemplateId(), user, mode.respectAnonPerms);

        final TemplateLayout layout = DotTemplateTool.themeLayout(template.getInode());

        final Map<String, ContainerView> mappedContainers = this.getMappedContainers(template, user);

        if (isRendered) {
            renderContainer(mappedContainers, velocityContext);
        }

        return new PageView(site, template, mappedContainers, page, layout);
    }

    private void renderContainer(final Map<String, ContainerView> containers, final Context velocityContext )
            throws DotDataException {

        for (final ContainerView containerView : containers.values()) {
            final Container container = containerView.getContainer();

            try {
                final String rendered = VelocityUtil.mergeTemplate("/live/" + container.getIdentifier() +
                        ".container", velocityContext);
                containerView.setRendered(rendered);
            } catch (Exception e) {
                throw new DotDataException(String.format("Container '%s' could not be " +
                        "rendered via " + "Velocity.", container.getIdentifier()), e);
            }
        }
    }

    private Map<String, ContainerView> getMappedContainers(final Template template, final User user)
            throws DotSecurityException, DotDataException {

        final List<Container> templateContainers = this.templateAPI.getContainersInTemplate(template, user, false);

        final Map<String, ContainerView> mappedContainers = new LinkedHashMap<>();
        for (final Container container : templateContainers) {
            final List<ContainerStructure> containerStructures = this.containerAPI.getContainerStructures(container);
            mappedContainers.put(container.getIdentifier(), new ContainerView(container, containerStructures));
        }

        return mappedContainers;
    }

    /**
     * Converts the specified {@link PageView} object to JSON format.
     *
     * @param pageView The representation of an HTML Page and it associated objects.
     * @return The JSON representation of the {@link PageView}.
     * @throws JsonProcessingException An error occurred when generating the JSON data.
     */
    public String asJson(final PageView pageView) throws JsonProcessingException {
        final ObjectWriter objectWriter = JsonMapper.mapper.writer().withDefaultPrettyPrinter();
        return objectWriter.writeValueAsString(pageView);
    }

    public Template saveTemplate(final User user, final String pageId, final PageForm pageForm)
            throws BadRequestException, DotDataException, DotSecurityException, IOException {

        final Contentlet page = this.contentletAPI.findContentletByIdentifier(pageId, false,
                langAPI.getDefaultLanguage().getId(), user, false);

        if (page == null) {
            throw new NotFoundException("An error occurred when proccessing the JSON request");
        }

        try {
            Template templateSaved = this.saveTemplate(page, user, pageForm);

            String templateId = page.getStringProperty(HTMLPageAssetAPI.TEMPLATE_FIELD);

            if (!templateId.equals( templateSaved.getIdentifier() )) {
                page.setStringProperty(Contentlet.INODE_KEY, null);
                page.setStringProperty(HTMLPageAssetAPI.TEMPLATE_FIELD, templateSaved.getIdentifier());
                this.contentletAPI.checkin(page, user, false);
            }

            return templateSaved;
        } catch (Exception e) {
            throw new DotRuntimeException(e);
        }
    }

    public Contentlet getPage(final User user, final String pageId) throws DotSecurityException, DotDataException {
        return this.contentletAPI.findContentletByIdentifier(pageId, false,
                langAPI.getDefaultLanguage().getId(), user, false);
    }

    public Template saveTemplate(final Contentlet page, final User user, final PageForm pageForm)
            throws BadRequestException, DotDataException, DotSecurityException, IOException {

        
        try {
            final Host host = getHost(pageForm.getHostId(), user);
            Template template = getTemplate(page, user, pageForm);

            return this.templateAPI.saveTemplate(template, host, user, false);
        } catch (Exception e) {
            throw new DotRuntimeException(e);
        }
    }

    public Template saveTemplate(final User user, final PageForm pageForm)
            throws BadRequestException, DotDataException, DotSecurityException, IOException {
        return this.saveTemplate(null, user, pageForm);
    }

    private Template getTemplate(Contentlet page, User user, PageForm form) throws DotDataException, DotSecurityException {

        Template result = null;
        String templateId = page != null ? page.getStringProperty(HTMLPageAssetAPI.TEMPLATE_FIELD) : null;

        if (UtilMethods.isSet(templateId)) {
            result = this.templateAPI.findWorkingTemplate(templateId, user, false);

            if (!UtilMethods.isSet(form.getTitle()) && !result.isAnonymous()) {
                result = new Template();
            }
        } else {
            result = new Template();
        }

        result.setTitle(form.getTitle());
        result.setTheme(form.getThemeId());
        result.setDrawedBody(form.getLayout());

        return result;
    }

    private Host getHost(final String hostId, final User user) {
        try {
            return UtilMethods.isSet(hostId) ? hostAPI.find(hostId, user, false) :
                        hostWebAPI.getCurrentHost(HttpServletRequestThreadLocal.INSTANCE.getRequest());
        } catch (DotDataException | DotSecurityException | PortalException | SystemException e) {
            throw new DotRuntimeException(e);
        }
    }

    public Contentlet getContentlet(final User user, final PageMode mode, final Language id, final String contentletId)
            throws DotDataException, DotSecurityException {

        final ShortyId contentShorty = APILocator.getShortyAPI()
                .getShorty(contentletId)
                .orElseGet(() -> {
                    throw new ResourceNotFoundException("Can't find contentlet:" + contentletId);
                });

        return APILocator.getContentletAPI()
                .findContentletByIdentifier(contentShorty.longId, mode.showLive, id.getId(), user, mode.isAdmin);
    }

    public Container getContainer(final String containerId, final User user, final PageMode mode)
            throws DotDataException, DotSecurityException {

        final ShortyId containerShorty = APILocator.getShortyAPI()
                .getShorty(containerId)
                .orElseGet(() -> {
                    throw new ResourceNotFoundException("Can't find Container:" + containerId);
                });
        return (mode.showLive) ? (Container) APILocator.getVersionableAPI()
                .findLiveVersion(containerShorty.longId, user, !mode.isAdmin)
                : (Container) APILocator.getVersionableAPI()
                .findWorkingVersion(containerShorty.longId, user, !mode.isAdmin);
    }

    public void checkPermission(final User user, final Contentlet contentlet, final Container container) throws DotSecurityException {
        APILocator.getPermissionAPI()
                .checkPermission(contentlet, PermissionLevel.READ, user);
        APILocator.getPermissionAPI()
                .checkPermission(container, PermissionLevel.EDIT, user);
    }

    public void checkPagePermission(final User user, final Contentlet htmlPageAsset) throws DotSecurityException {
        APILocator.getPermissionAPI()
                .checkPermission(htmlPageAsset, PermissionLevel.EDIT, user);
    }
}
