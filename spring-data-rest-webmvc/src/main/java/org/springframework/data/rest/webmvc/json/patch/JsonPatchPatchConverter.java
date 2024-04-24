/*
 * Copyright 2014-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.rest.webmvc.json.patch;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.springframework.util.Assert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Convert {@link JsonNode}s containing JSON Patch to/from {@link Patch} objects.
 *
 * @author Craig Walls
 * @author Oliver Gierke
 * @author Mathias Düsterhöft
 * @author Oliver Trosien
 */
public class JsonPatchPatchConverter implements PatchConverter<JsonNode> {

	private final ObjectMapper mapper;
	private final BindContext context;

	public JsonPatchPatchConverter(ObjectMapper mapper, BindContext context) {

		Assert.notNull(mapper, "ObjectMapper must not be null");

		this.mapper = mapper;
		this.context = context;
	}

	/**
	 * Constructs a {@link Patch} object given a JsonNode.
	 *
	 * @param jsonNode a JsonNode containing the JSON Patch
	 * @return a {@link Patch}
	 */
	public Patch convert(JsonNode jsonNode) {

		if (!(jsonNode instanceof ArrayNode)) {
			throw new IllegalArgumentException("JsonNode must be an instance of ArrayNode");
		}

		ArrayNode opNodes = (ArrayNode) jsonNode;
		List<PatchOperation> ops = new ArrayList<>(opNodes.size());

		for (Iterator<JsonNode> elements = opNodes.elements(); elements.hasNext();) {

			JsonNode opNode = elements.next();

			String opType = opNode.get("op").textValue();
			String path = opNode.get("path").textValue();

			JsonNode valueNode = opNode.get("value");
			Object value = valueFromJsonNode(path, valueNode);
			String from = opNode.has("from") ? opNode.get("from").textValue() : null;

			if ("test".equals(opType)) {
				ops.add(TestOperation.whetherValueAt(path).hasValue(value));
			} else if ("replace".equals(opType)) {
				ops.add(ReplaceOperation.valueAt(path).with(value));
			} else if ("remove".equals(opType)) {
				ops.add(RemoveOperation.valueAt(path));
			} else if ("add".equals(opType)) {
				ops.add(AddOperation.of(path, value));
			} else if ("copy".equals(opType)) {
				ops.add(CopyOperation.from(from).to(path));
			} else if ("move".equals(opType)) {
				ops.add(MoveOperation.from(from).to(path));
			} else {
				throw new PatchException("Unrecognized operation type: " + opType);
			}
		}

		return new Patch(ops, context);
	}

	private Object valueFromJsonNode(String path, JsonNode valueNode) {

		if (valueNode == null || valueNode.isNull()) {
			return null;
		} else if (valueNode.isTextual()) {
			return valueNode.asText();
		} else if (valueNode.isFloatingPointNumber()) {
			return valueNode.asDouble();
		} else if (valueNode.isBoolean()) {
			return valueNode.asBoolean();
		} else if (valueNode.isInt()) {
			return valueNode.asInt();
		} else if (valueNode.isLong()) {
			return valueNode.asLong();
		} else if (valueNode.isObject() || valueNode.isArray()) {
			return new JsonLateObjectEvaluator(mapper, valueNode);
		}

		throw new PatchException(
				String.format("Unrecognized valueNode type at path %s and value node %s", path, valueNode));
	}
}
