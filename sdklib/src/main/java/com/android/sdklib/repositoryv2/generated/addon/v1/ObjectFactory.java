//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.11 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2015.11.23 at 03:30:50 PM PST 
//


package com.android.sdklib.repositoryv2.generated.addon.v1;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;
import com.android.repository.api.Repository;
import com.android.repository.impl.generated.v1.RepositoryType;
import com.android.sdklib.repositoryv2.meta.AddonFactory;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the com.android.sdklib.repositoryv2.generated.addon.v1 package. 
 * <p>An ObjectFactory allows you to programatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
@SuppressWarnings("override")
public class ObjectFactory
    extends AddonFactory
{

    private final static QName _SdkAddon_QNAME = new QName("http://schemas.android.com/sdk/android/repo/addon2/01", "sdk-addon");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: com.android.sdklib.repositoryv2.generated.addon.v1
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link AddonDetailsType }
     * 
     */
    public AddonDetailsType createAddonDetailsType() {
        return new AddonDetailsType();
    }

    /**
     * Create an instance of {@link LibrariesType }
     * 
     */
    public com.android.sdklib.repositoryv2.meta.DetailsTypes.AddonDetailsType.Libraries createLibrariesType() {
        return new LibrariesType();
    }

    /**
     * Create an instance of {@link ExtraDetailsType }
     * 
     */
    public ExtraDetailsType createExtraDetailsType() {
        return new ExtraDetailsType();
    }

    /**
     * Create an instance of {@link MavenType }
     * 
     */
    public MavenType createMavenType() {
        return new MavenType();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link RepositoryType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://schemas.android.com/sdk/android/repo/addon2/01", name = "sdk-addon")
    public JAXBElement<RepositoryType> createSdkAddon(RepositoryType value) {
        return new JAXBElement<RepositoryType>(_SdkAddon_QNAME, RepositoryType.class, null, value);
    }

    public JAXBElement<Repository> generateElement(Repository value) {
        return ((JAXBElement) createSdkAddon(((RepositoryType) value)));
    }

}
