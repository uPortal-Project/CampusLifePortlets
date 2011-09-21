package org.jasig.portlet.dining.dao;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import javax.portlet.PortletRequest;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.jasig.portlet.dining.model.menu.xml.DiningHall;
import org.jasig.portlet.dining.model.menu.xml.Menu;
import org.owasp.validator.html.AntiSamy;
import org.owasp.validator.html.CleanResults;
import org.owasp.validator.html.Policy;
import org.owasp.validator.html.PolicyException;
import org.owasp.validator.html.ScanException;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.core.io.Resource;

public class YaleDiningMenuDaoImpl implements IDiningMenuDao {

    protected final Log log = LogFactory.getLog(getClass());

    private static final TransformerFactory transformerFactory = TransformerFactory.newInstance();

    private Cache cache;

    @Required
    public void setCache(Cache cache) {
        this.cache = cache;
    }

    private Resource xslt;
    
    public void setXslt(Resource xslt) {
        this.xslt = xslt;
    }
    
    private Policy policy;
    
    public void setPolicy(Resource config) throws PolicyException, IOException {
        this.policy = Policy.getInstance(config.getFile());
    }
    
    private List<String> diningHalls;
    
    public void setDiningHalls(List<String> diningHalls) {
        this.diningHalls = diningHalls;
    }

    @Override
    public List<String> getDiningHalls(PortletRequest request) {
        return this.diningHalls;
    }
    
    @Override
    public DiningHall getMenu(PortletRequest request, String diningHall) {

        String key = "diningHallMenu.".concat(diningHall);
        Element cachedElement = this.cache.get(key);
        if (cachedElement == null) {
            
            try {
                
                final String htmlContent = getHtmlContent(diningHall);
                final String cleanedContent = getCleanedHtmlContent(htmlContent);
                final String xml = getXml(cleanedContent);
                final Menu menu = getMenu(xml);
                cachedElement = new Element(key, menu.getDiningHall().get(0));
                this.cache.put(cachedElement);
                
            } catch (ClientProtocolException e) {
                log.error("Failed to retrieve dining hall menu", e);
            } catch (TransformerConfigurationException e) {
                throw new RuntimeException("Failed to create identity transformer to serialize Node to String", e);
            } catch (TransformerException e) {
                throw new RuntimeException("Failed to convert Node to String using Transformer", e);
            } catch (IOException e) {
                log.error("Failed to retrieve dining hall menu", e);
            } catch (JAXBException e) {
                log.error("Failed to parse dining hall menu", e);
            } catch (ScanException e) {
                log.error("Failed to parse dining hall menu", e);
            } catch (PolicyException e) {
                log.error("Failed to parse dining hall menu", e);
            }
        }
        
        if (cachedElement != null) {
            return (DiningHall) cachedElement.getValue();
        } else {
            return null;
        }

    }
    
    protected String getHtmlContent(String diningHall) throws ClientProtocolException, IOException {
        final DefaultHttpClient httpclient = new DefaultHttpClient();
        final HttpGet httpget = new HttpGet("http://www.yaledining.org/menu.cfm?mDH=" + diningHall);
        final HttpResponse response = httpclient.execute(httpget);
        final InputStream httpStream = response.getEntity().getContent();
        final String content = IOUtils.toString(httpStream);
        return content;
    }
    
    protected String getCleanedHtmlContent(String html) throws ScanException, PolicyException {
        final AntiSamy as = new AntiSamy();
        final CleanResults cr = as.scan(html, policy);
        final String cleanedHtml = cr.getCleanHTML();  
        final String unescaped = cleanedHtml.replace("&", "&amp;");
        return unescaped;
    }

    protected String getXml(String cleanHtml) throws TransformerException, IOException {
        final StreamSource xsltSource = new StreamSource(xslt.getInputStream());
        final Transformer identityTransformer = transformerFactory.newTransformer(xsltSource);
        identityTransformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        final StringWriter outputWriter = new StringWriter();
        final StreamResult outputTarget = new StreamResult(outputWriter);
        final StreamSource xmlSource = new StreamSource(new StringReader(cleanHtml));
        identityTransformer.transform(xmlSource, outputTarget);
        final String content = outputWriter.toString();
        return content;
    }
    
    protected Menu getMenu(String xml) throws JAXBException {
        final String packageName = Menu.class.getPackage().getName();
        final JAXBContext jc = JAXBContext.newInstance( packageName );
        final Unmarshaller u = jc.createUnmarshaller();
        final Menu menu = (Menu) u.unmarshal(IOUtils.toInputStream(xml));
        return (Menu) menu;
    }

}
