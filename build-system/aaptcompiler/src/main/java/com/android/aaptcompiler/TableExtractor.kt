package com.android.aaptcompiler

import com.android.aapt.Resources
import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.namespace.QName
import javax.xml.stream.XMLEventReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.events.Attribute
import javax.xml.stream.events.Comment
import javax.xml.stream.events.StartElement

private val xmlInputFactory = XMLInputFactory.newFactory()

/**
 * Namespace uri for the xliff:g tag in XML.
 *
 * This is used to identify the xliff:g spans in processed strings, in order to mark the
 * untranslatable sections of string resources.
 */
private const val XLIFF_NS_URI = "urn:oasis:names:tc:xliff:document:1.2"

/**
 * Resource parsed from the XML, with all relevant metadata.
 *
 * @property config The config description of the resource. This should be the same as the config
 *   of the source.
 * @property source The start location in the xml from which this resource was extracted.
 * @property comment The comment describing the resource that appeared before it in the xml. This
 *   will be an empty string if no comment was supplied.
 */
private class ParsedResource(
  val config: ConfigDescription, val source: Source, val comment: String) {
  /** The name of the resource extraccted from the xml. */
  var name: ResourceName = ResourceName.EMPTY
  /** The product name for the given resource value. */
  var productString: String = ""
  /** The id of the resource. A value of 0 means the id was not supplied. */
  var resourceId = 0
  /** The visibility of the extracted resource. */
  var visibility = ResourceVisibility.UNDEFINED
  /** Whether the resource has <add-resource> in an overlay. */
  var allowNew = false
  /**
   * The overlayable representation of this resource. This value is {@code null} if it is not
   * overlayable.
   */
  var overlayableItem: OverlayableItem? = null
  /**
   * The value of the resource, this might be null if this is a use of a resource. (i.e. an
   * <attr> within a <declare-styleable>
   */
  var value : Value? = null
  /**
   * The child resources of the given resource. These resources will be added to the table when
   * {@code this} is added. The connection of the resources to this resource should be reflected in
   * the [value] of the parsed resource.
   */
  val children = mutableListOf<ParsedResource>()
}

/**
 * All options for the Table Extractor.
 *
 * @property translatable Whether the default setting for this parser is to allow translation.
 *
 * @property errorOnPositionalArgs Whether positional arguments in formatted strings are treated as
 *   errors or warnings.
 *
 * @property visibility the default visibility of resources extracted. If non-null, all new
 *   resources are set with this visibility, and will error if trying to parse the <public>,
 *   <public-group>, <java-symbol> or <symbol> tags.
 */
data class TableExtractorOptions(
  val translatable: Boolean = true,
  val errorOnPositionalArgs: Boolean = true,
  val visibility: ResourceVisibility? = null)

/** Returns true if the element is <skip> or <eat-comment> and can be safely ignored */
fun shouldIgnoreElement(elementName: QName): Boolean {
  return elementName.namespaceURI.isEmpty() &&
    (elementName.localPart == "skip" || elementName.localPart == "eat-comment")
}

fun parseFormatNoEnumsOrFlags(name: String): Int =
  when (name) {
    "reference" -> Resources.Attribute.FormatFlags.REFERENCE_VALUE
    "string" -> Resources.Attribute.FormatFlags.STRING_VALUE
    "integer" -> Resources.Attribute.FormatFlags.INTEGER_VALUE
    "boolean" -> Resources.Attribute.FormatFlags.BOOLEAN_VALUE
    "color" -> Resources.Attribute.FormatFlags.COLOR_VALUE
    "float" -> Resources.Attribute.FormatFlags.FLOAT_VALUE
    "dimension" -> Resources.Attribute.FormatFlags.DIMENSION_VALUE
    "fraction" -> Resources.Attribute.FormatFlags.FRACTION_VALUE
    else -> 0
  }

fun parseFormatType(name: String): Int =
  when (name) {
    "enum" -> Resources.Attribute.FormatFlags.ENUM_VALUE
    "flags" -> Resources.Attribute.FormatFlags.FLAGS_VALUE
    else -> parseFormatNoEnumsOrFlags(name)
  }

fun parseFormatAttribute(value: String): Int {
  var mask = 0
  for (part in value.split('|')) {
    val type = parseFormatType(part.trim())
    if (type == 0) {
      return 0
    }
    mask = mask or type
  }
  return mask
}

/**
 * Class that parses an XML file for resources and adds them to a ResourceTable.
 *
 * <p> Primarily, as each resource needs to be parsed, the name, package, and type of resource is
 * extracted.
 *
 * <p> Then, if an item, a call to [parseItem] is invoked with a type mask of the valid types
 * the xml element can be parsed as. This will result in a more specialized call to [parseXml]
 * which will proceed to flatten the xml subtree of the item (which may include span tags and
 * untranslatable section tags) and then attempt to process the flattened xml string (in accordance
 * with the valid types specified by the type mask). Finally, the parsed resource value will be
 * added to [ResourceTable], if successful.
 *
 * <p> Bag types (resources whose xml element's children also represent resources), i.e. array, are
 * handled a little differently. After the resource name is extracted, the appropriate bag-parsing
 * method is invoked, which will in turn call [parseItem] with a type mask of the valid types that
 * the bag allows. After all child elements have been parsed successfully, the bag resource and all
 * child resources are added to the [ResourceTable]
 *
 * @property table The resource table for the extracted resources to be added.
 * @property source The source of the extracted resources.
 * @property config The config description of the source.
 * @property options The options with how the resources should be extracted.
 */
class TableExtractor(
  val table: ResourceTable,
  val source: Source,
  val config: ConfigDescription,
  val options: TableExtractorOptions) {

  fun extract(inputFile: InputStream) : Boolean {
    var eventReader : XMLEventReader? = null
    try {
      eventReader = xmlInputFactory.createXMLEventReader(inputFile)

      val documentStart = eventReader.nextEvent()
      if (!documentStart.isStartDocument) {
        // TODO(b/139297538): diagnostics
        return false
      }

      var rootStart = eventReader.nextEvent()
      while (!rootStart.isStartElement) {
        // ignore comments and text before the root tag
        rootStart = eventReader.nextEvent()
      }

      val rootName = rootStart.asStartElement().name
      if (rootName.namespaceURI != null && rootName.localPart != "resources") {
        // TODO(b/139297538): diagnostics
        return false
      }

      return extractResourceValues(eventReader)
    } catch (xmlException: XMLStreamException) {
      if (xmlException.message?.contains("Premature end of file.", true) != true) {
        // Having no root is not an error, but any other xml format exception is.
        throw xmlException
      }
      return true
    } finally {
      eventReader?.close()
    }
  }


  /**
   * Extracts all the resources from the given eventReader.
   *
   * <p> The eventReader is assumed have just read the root "resources" start element. All resource
   * values extracted are added to the [table] property.
   *
   * @param eventReader: The source of the resources to extract. This is expected to be directly
   * after the root xml element when this method is invoked. The eventReader will be after the
   * corresponding end element when this method returns.
   */
  private fun extractResourceValues(eventReader: XMLEventReader): Boolean {

    var error = false

    var comment = ""

    while (eventReader.hasNext()) {
      val event = eventReader.nextEvent()

      if (event.eventType == XMLStreamConstants.COMMENT) {
        comment = (event as Comment).text.trim()
        continue
      }

      if (event.isCharacters) {
        if (!event.asCharacters().isWhiteSpace) {
          // whitespace is not allowed here
          // TODO(b/139297538): diagnostics
          error = true
        }
        continue
      }

      if (event.isEndElement) {
        // we've exhausted all resources
        break
      }

      if (!event.isStartElement) {
        // TODO(b/139297538): diagnostics
        error = true
      }

      val element = event.asStartElement()
      val elementName = element.name
      if (elementName.namespaceURI.isNotEmpty()) {
        // skip unrecognized namespaces
        walkToEndOfElement(element.asStartElement(), eventReader)
        continue
      }

      if (elementName.localPart == "skip" || elementName.localPart == "eat-comment") {
        comment = ""
        walkToEndOfElement(element.asStartElement(), eventReader)
        continue
      }

      val parsedResource =
        ParsedResource(config, source.withLine(element.location.lineNumber), comment)
      comment = ""

      // extract the product name if possible
      val productName = element.getAttributeByName(QName("product"))
      if (productName != null) {
        parsedResource.productString = productName.value
      }

      if (!extractResource(element, eventReader, parsedResource)) {
        error = true
      }

      if (!addResourcesToTable(parsedResource)) {
        error = true
      }
    }

    return !error
  }

  /**
   * Extracts the [Value] of a resource from the given element. This can be either an [Item] or a
   *   nested value type.
   *
   * @param element The start of the element to be translated as a [Value].
   * @param eventReader The xml to be read. The event reader should have just pulled the
   *   {@code StartElement} element. After this method is invoked the eventReader will be placed
   *   after the corresponding end tag for element.
   * @param parsedResource The [ParsedResource] to hold the extracted value upon success.
   * @return Whether or not the parsing was a success.
   */
  private fun extractResource(
    element : StartElement,
    eventReader : XMLEventReader,
    parsedResource : ParsedResource): Boolean {

    var resourceTypeName = element.name.localPart

    // the format of the value of this resource.
    var resourceFormat = 0

    var canBeItem = true
    var canBeBag = true

    if (resourceTypeName == "item") {
      canBeBag = false

      // the default format for <item> is any. This can be overridden by the format attribute
      resourceFormat = Resources.Attribute.FormatFlags.ANY_VALUE

      val formatAttribute = element.getAttributeByName(QName("format"))
      if (formatAttribute != null) {
        resourceFormat = parseFormatNoEnumsOrFlags(formatAttribute.value)
        if (resourceFormat == 0) {
          // TODO(b/139297538): diagnostics
          walkToEndOfElement(element, eventReader)
          return false
        }
      }

      // Items have their type encoded in the type attribute.
      val typeAttribute = element.getAttributeByName(QName("type"))
      if (typeAttribute == null) {
        // TODO(b/139297538): diagnostics
        walkToEndOfElement(element, eventReader)
        return false
      }
      resourceTypeName = typeAttribute.value

    } else if (resourceTypeName == "bag") {
      canBeItem = false

      // Bags have their type encoded in the type attribute.
      val typeAttribute = element.getAttributeByName(QName("type"))
      if (typeAttribute == null) {
        // TODO(b/139297538): diagnostics
        walkToEndOfElement(element, eventReader)
        return false
      }
    }

    // get name of the resource. This will be checked later, because not all xml elements require
    // a name.
    val nameAttribute = element.getAttributeByName(QName("name"))

    if (resourceTypeName == "id") {
      if (nameAttribute == null) {
        // TODO(b/139297538): diagnostics
        walkToEndOfElement(element, eventReader)
        return false
      }

      parsedResource.name =
        parsedResource.name.copy(type = AaptResourceType.ID, entry = nameAttribute.value)
      parseItem(element, eventReader, parsedResource, resourceFormat)

      val item = parsedResource.value
      when {
        item is BasicString && item.ref.value().isEmpty() ->
          // If no inner element exists, represent a unique identifier
          parsedResource.value = Id()
        item is Reference && item.id != null ->
          // A null reference also means there is no inner element when ids are in the form:
          //    <id name="name"/>
          parsedResource.value = Id()
        (item is Reference && item.name.type != AaptResourceType.ID) || item !is Reference ->
          // if an inner element exists, the inner element must be a reference to another id
          // TODO(b/139297538): diagnostics
          return false
      }
      return true
    }

    if (canBeItem) {
      val (type, typeMask) = when (resourceTypeName) {
        "bool" -> Pair(AaptResourceType.BOOL, Resources.Attribute.FormatFlags.BOOLEAN_VALUE)
        "color" -> Pair(AaptResourceType.COLOR, Resources.Attribute.FormatFlags.COLOR_VALUE)
        "configVarying" ->
          Pair(AaptResourceType.CONFIG_VARYING, Resources.Attribute.FormatFlags.ANY_VALUE)
        "dimen" ->
          Pair(
            AaptResourceType.DIMEN,
            Resources.Attribute.FormatFlags.FLOAT_VALUE or
              Resources.Attribute.FormatFlags.FRACTION_VALUE or
              Resources.Attribute.FormatFlags.DIMENSION_VALUE)
        "drawable" -> Pair(AaptResourceType.DRAWABLE, Resources.Attribute.FormatFlags.COLOR_VALUE)
        "fraction" ->
          Pair(
            AaptResourceType.FRACTION,
            Resources.Attribute.FormatFlags.FLOAT_VALUE or
              Resources.Attribute.FormatFlags.FRACTION_VALUE or
              Resources.Attribute.FormatFlags.DIMENSION_VALUE)
        "integer" -> Pair(AaptResourceType.INTEGER, Resources.Attribute.FormatFlags.INTEGER_VALUE)
        "string" -> Pair(AaptResourceType.STRING, Resources.Attribute.FormatFlags.STRING_VALUE)
        else -> Pair(null, Resources.Attribute.FormatFlags.ANY_VALUE)
      }
      if (type != null) {
        // this is an item record its type and format and start parsing.
        if (nameAttribute == null) {
          // TODO(b/139297538): diagnostics
          walkToEndOfElement(element, eventReader)
          return false
        }

        parsedResource.name = ResourceName( "", type, nameAttribute.value)

        // Only use the implied format of the type when there is no explicit format.
        if (resourceFormat == 0) {
          resourceFormat = typeMask
        }
        return parseItem(element, eventReader, parsedResource, resourceFormat)
      }
    }

    if (canBeBag) {
      // TODO(b132800341): add Bag parsing methods.
    }

    if (canBeItem) {
      val parsedType = resourceTypeFromTag(resourceTypeName)
      if (parsedType != null) {
        if (nameAttribute == null) {
          // TODO(b/139297538): diagnostics
          walkToEndOfElement(element, eventReader)
          return false
        }

        parsedResource.name = ResourceName("", parsedType, nameAttribute.value)
        parsedResource.value =
          parseXml(element, eventReader, Resources.Attribute.FormatFlags.REFERENCE_VALUE, false)

        if (parsedResource.value == null) {
          // TODO(b/139297538): diagnostics
          return false
        }

        return true
      }
    }

    // TODO(b/139297538): diagnostics
    walkToEndOfElement(element, eventReader)
    return false
  }

  /**
   * Parses the XML subtree and returns an Item.
   *
   * @param element The start of the element to be translated as an item type.
   * @param eventReader The xml to be read. The event reader should have just pulled the
   *   {@code StartElement} element. After this method is invoked the eventReader will be placed
   *   after the corresponding end tag for element.
   * @param resourceFormat A type mask that specifies which formats are valid for the xml to be
   *   interpreted as.
   * @param allowRawString If true, a [RawString] representing the xml is returned if it could not
   *   be parsed as any valid resource [Item]. If false, {@code null} will be returned instead on
   *   failure.
   *
   * @return The [Item] that represents the xml subtree. This will be {@code null} if the xml failed
   *   to be interpreted as a valid resource.
   */
  private fun parseXml(
    element: StartElement,
    eventReader: XMLEventReader,
    resourceFormat: Int,
    allowRawString : Boolean) : Item? {

    val flattenedXml = flattenXmlSubTree(eventReader)
    if (!flattenedXml.success) {
      return null
    }

    if (flattenedXml.styleString.spans.isNotEmpty()) {
      // can only be a StyledString
      return StyledString(
        table.stringPool.makeRef(
          flattenedXml.styleString,
          StringPool.Context(StringPool.Context.Priority.NORMAL.priority, config)),
        flattenedXml.untranslatableSections)
    }

    // Process the raw value
    val processedItem =
      tryParseItemForAttribute(flattenedXml.rawString, resourceFormat) {
        val id = Id()
        id.source = source
        table.addResource(it, ConfigDescription(), "", id)
      }

    if (processedItem != null) {
      // Fix up the reference.
      if (processedItem is Reference) {
        resolvePackage(element, processedItem)
      }
      return processedItem
    }

    // Try making a regular string.
    if (resourceFormat and Resources.Attribute.FormatFlags.STRING_VALUE != 0) {
      // use trimmed escaped string.
      return BasicString(
        table.stringPool.makeRef(
          flattenedXml.styleString.str, StringPool.Context(config = config)),
        flattenedXml.untranslatableSections)
    }

    // if the text is empty, and the value is not allowed to be a string, encode it as a @null.
    if (flattenedXml.rawString.trim().isEmpty()) {
      return makeNull()
    }

    if (allowRawString) {
      return RawString(
        table.stringPool.makeRef(flattenedXml.rawString, StringPool.Context(config=config)))
    }

    return null
  }

  /**
   * Attempts to parse the xml subtree as an item resource.
   *
   * @param element The start of the element to be translated as an item type.
   * @param eventReader The xml to be read. The event reader should have just pulled the
   *   {@code StartElement} element. After this method is invoked the eventReader will be placed
   *   after the corresponding end tag for element.
   * @param parsedResource The resource to put the parsed [Item] into, if successful.
   * @param resourceFormat A type mask that specifies which formats are valid for the xml to be
   *   interpreted as.
   * @return Whether or not the xml could be parsed.
   */
  private fun parseItem(
    element: StartElement,
    eventReader: XMLEventReader,
    parsedResource: ParsedResource,
    resourceFormat: Int) : Boolean {

    if (resourceFormat == Resources.Attribute.FormatFlags.STRING_VALUE) {
      return parseString(element, eventReader, parsedResource)
    }

    parsedResource.value = parseXml(element, eventReader, resourceFormat, false)
    if (parsedResource.value == null) {
      // TODO(b/139297538): DIAG
      return false
    }
    return true
  }

  /**
   * Attempts to parse the xml element as a String, including whether the string is formatted or
   * translatable.
   *
   * @param element The start of the element to be translated at a string.
   * @param eventReader The xml to be read. The event reader should have just pulled the
   *   {@code StartElement} element. After this method is invoked the eventReader will be placed
   *   after the corresponding end tag for element.
   * @param parsedResource the resource to put the parsed String into. The [ParsedResource.value]
   *   will be set to either a [BasicString] or [StyleString] resource, if successful.
   * @return Whether or not the element could be parsed as a String resource.
   */
  private fun parseString(
    element: StartElement, eventReader: XMLEventReader, parsedResource: ParsedResource): Boolean {
    var formatted = true
    val formattedAttribute = element.getAttributeByName(QName("formatted"))
    if (formattedAttribute != null) {
      val maybeFormatted = parseAsBool(formattedAttribute.value)
      if (maybeFormatted == null) {
        // TODO(b/139297538): diagnostics
        walkToEndOfElement(element, eventReader)
        return false
      }
      formatted = maybeFormatted
    }

    var translatable = options.translatable
    val translatableAttribute = element.getAttributeByName(QName("translatable"))
    if (translatableAttribute != null) {
      val maybeTranslatable = parseAsBool(translatableAttribute.value)
      if (maybeTranslatable == null) {
        // TODO(b/139297538): diagnostics
        walkToEndOfElement(element, eventReader)
        return false
      }
      translatable = maybeTranslatable
    }

    val value =
      parseXml(element, eventReader, Resources.Attribute.FormatFlags.STRING_VALUE, false)
    if (value == null) {
      // TODO(b/139297538): diagnostics
      return false
    }

    if (value is BasicString) {
      value.translatable = translatable

      if (formatted && translatable) {
        if (!verifyJavaStringFormat(value.toString())) {
          // TODO(b/139297538): diagnostics
          return false
        }
      }
    } else if (value is StyledString) {
      value.translatable = translatable
    }
    parsedResource.value = value
    return true
  }

  /**
   * Parses the XML subtree as a StyleString (flattened XML representation for strings with
   * formatting).
   *
   * @param eventReader the xml to flattened. The reader should have just read the start of the
   *   element that is needed to be flattened. After this method is invoked, the event reader will
   *   be after the end of the element that the flattened xml is to represent.
   * @return
   *   <p> If Parsing fails, the [FlattenedXml.success] fill be set to false and the rest of the
   *   flattened xml will be left in a unspecified state.
   *   <p> Otherwise:
   *   [FlattenedXml.styleString] contains the escaped and whitespace trimmed text with included
   *     spans.
   *   [FlattenedXml.rawString] contains the unescaped text.
   *   [FlattenedXml.untranslatableSections] contains the sections of the string that should not be
   *     translated.
   */
  private fun flattenXmlSubTree(eventReader: XMLEventReader) : FlattenedXml {
    var depth = 1

    val builder = XmlStringBuilder()

    while (depth > 0) {
      val event = eventReader.nextEvent()

      when {
        event.isCharacters -> builder.append(event.asCharacters().data)

        event.isStartElement -> {

          val element = event.asStartElement()
          val elementName = element.name

          when (elementName.namespaceURI) {
            XMLConstants.NULL_NS_URI -> {
              // This is an HTML tag which we encode as a span.
              val spanName = StringBuilder(elementName.localPart)
              val attributes = element.attributes
              while (attributes.hasNext()) {
                val attribute = attributes.next() as Attribute
                spanName.append(";${attribute.name.localPart}=${attribute.value}")
              }
              builder.startSpan(spanName.toString())
            }
            XLIFF_NS_URI -> {
              // This is an XLIFF tag which is not encoded as a span.
              if (elementName.localPart == "g") {
                // start untranslatable 'g' tag. Unknown XLIFF tags are ignored.
                builder.startUntranslatable()
              }
            }
            else -> {
              // besides XLIFF, any other namespaced tags are unsupported and ignored.
              // TODO(b/139297538): diagnostics, warn here
            }

          }
          ++depth
        }
        event.isEndElement -> {

          val element = event.asEndElement()
          val elementName = element.name

          --depth
          when (elementName.namespaceURI) {
            XMLConstants.NULL_NS_URI -> {
              if (depth != 0) {
                builder.endSpan()
              }
            }
            XLIFF_NS_URI -> {
              if (elementName.localPart == "g") {
                builder.endUntranslatable()
              }
            }
          }
        }
      }
    }

    val flattenedXml = builder.getFlattenedXml()
    if (builder.error.isNotEmpty()) {
      // TODO(b/139297538): diagnostics
    }
    return flattenedXml
  }

  /**
   * Adds the given parsed resource to the [table] property.
   *
   * @param parsedResource the resource parsed from xml.
   * @return Whether or not the resource was successfully added to the table.
   */
  private fun addResourcesToTable(parsedResource: ParsedResource): Boolean {
    if (parsedResource.visibility != ResourceVisibility.UNDEFINED) {
      val visibility =
        Visibility(parsedResource.source, parsedResource.comment, parsedResource.visibility)
      if (!table.setVisibilityWithId(parsedResource.name, visibility, parsedResource.resourceId)) {
        return false
      }
    }

    if (parsedResource.allowNew) {
      val allowNew = AllowNew(parsedResource.source, parsedResource.comment)
      if (!table.setAllowNew(parsedResource.name, allowNew)) {
        return false
      }
    }

    val overlayableItem = parsedResource.overlayableItem
    if (overlayableItem != null) {
      if (!table.setOverlayable(parsedResource.name, overlayableItem)) {
        return false
      }
    }

    val resource = parsedResource.value
    if (resource != null) {
      // Attach the comment, source and config to the resource.
      resource.comment = parsedResource.comment
      resource.source = parsedResource.source

      if (!table.addResourceWithId(
          parsedResource.name,
          parsedResource.resourceId,
          parsedResource.config,
          parsedResource.productString,
          resource)) {
        return false
      }
    }

    var error = false

    for (child in parsedResource.children) {
      error = error || !addResourcesToTable(child)
    }
    return !error
  }

  /**
   * Polls the {@code eventReader} till the reader is after the corresponding end element of
   * {@code element}.
   *
   * <p> It is assumed that {@code element} is the last startElement read from
   * the reader.
   *
   * @param element The start of the element to which we want to reach the end of.
   * @param eventReader The eventReader to be moved to the end of the corresponding
   *   {@code EndElement}.
   */
  private fun walkToEndOfElement(element: StartElement, eventReader: XMLEventReader) {
    var depth = 1
    while (eventReader.hasNext()) {
      val event = eventReader.nextEvent()

      if (event.isStartElement) {
        ++depth
      } else if (event.isEndElement) {
        --depth
        if (depth == 0) {
          // Sanity check.
          assert(event.asEndElement().name.localPart == element.name.localPart)
          break
        }
      }
    }
  }
}
