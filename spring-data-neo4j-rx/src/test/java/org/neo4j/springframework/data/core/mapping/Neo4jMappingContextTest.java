/*
 * Copyright (c) 2019-2020 "Neo4j,"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.springframework.data.core.mapping;

import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.internal.value.StringValue;
import org.neo4j.springframework.data.core.convert.Neo4jConversions;
import org.neo4j.springframework.data.core.schema.GeneratedValue;
import org.neo4j.springframework.data.core.schema.GraphPropertyDescription;
import org.neo4j.springframework.data.core.schema.Id;
import org.neo4j.springframework.data.core.schema.IdGenerator;
import org.neo4j.springframework.data.core.schema.Node;
import org.neo4j.springframework.data.core.schema.NodeDescription;
import org.neo4j.springframework.data.core.schema.Property;
import org.neo4j.springframework.data.core.schema.Relationship;
import org.neo4j.springframework.data.core.schema.RelationshipDescription;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mapping.Association;

/**
 * @author Michael J. Simons
 */
class Neo4jMappingContextTest {

	@Test
	void initializationOfSchemaShouldWork() {

		Neo4jMappingContext schema = new Neo4jMappingContext();
		schema.setInitialEntitySet(new HashSet<>(Arrays.asList(BikeNode.class, UserNode.class, TripNode.class)));
		schema.initialize();

		NodeDescription<?> optionalUserNodeDescription = schema.getNodeDescription("User");
		assertThat(optionalUserNodeDescription)
			.isNotNull()
			.satisfies(description -> {
				assertThat(description.getUnderlyingClass()).isEqualTo(UserNode.class);

				assertThat(description.getIdDescription().isInternallyGeneratedId()).isTrue();

				assertThat(description.getGraphProperties())
					.extracting(GraphPropertyDescription::getFieldName)
					.containsExactlyInAnyOrder("id", "name", "first_name");

				assertThat(description.getGraphProperties())
					.extracting(GraphPropertyDescription::getPropertyName)
					.containsExactlyInAnyOrder("id", "name", "firstName");

				Collection<String> expectedRelationships = Arrays.asList("[:OWNS] -> (:BikeNode)");
				Collection<RelationshipDescription> relationships = description.getRelationships();
				assertThat(relationships.stream().filter(r -> !r.isDynamic()))
					.allMatch(d -> expectedRelationships
						.contains(String.format("[:%s] -> (:%s)", d.getType(), d.getTarget().getPrimaryLabel())));
			});

		NodeDescription<?> optionalBikeNodeDescription = schema.getNodeDescription("BikeNode");
		assertThat(optionalBikeNodeDescription)
			.isNotNull()
			.satisfies(description -> {
				assertThat(description.getUnderlyingClass()).isEqualTo(BikeNode.class);

				assertThat(description.getIdDescription().isAssignedId()).isTrue();

				Collection<String> expectedRelationships = Arrays.asList("[:OWNER] -> (:User)", "[:RENTER] -> (:User)");
				Collection<RelationshipDescription> relationships = description.getRelationships();
				assertThat(relationships.stream().filter(r -> !r.isDynamic()))
					.allMatch(d -> expectedRelationships
						.contains(String.format("[:%s] -> (:%s)", d.getType(), d.getTarget().getPrimaryLabel())));
			});

		Neo4jPersistentEntity<?> bikeNodeEntity = schema.getPersistentEntity(BikeNode.class);

		assertThat(bikeNodeEntity.getPersistentProperty("owner").isAssociation()).isTrue();
		assertThat(bikeNodeEntity.getPersistentProperty("renter").isAssociation()).isTrue();
		assertThat(bikeNodeEntity.getPersistentProperty("dynamicRelationships").isAssociation()).isTrue();
		assertThat(bikeNodeEntity.getPersistentProperty("someValues").isAssociation()).isFalse();
		assertThat(bikeNodeEntity.getPersistentProperty("someMoreValues").isAssociation()).isFalse();
		assertThat(bikeNodeEntity.getPersistentProperty("evenMoreValues").isAssociation()).isFalse();
		assertThat(bikeNodeEntity.getPersistentProperty("funnyDynamicProperties").isAssociation()).isFalse();
	}

	@Test
	void shouldPreventIllegalIdAnnotations() {

		Neo4jMappingContext schema = new Neo4jMappingContext();
		schema.setInitialEntitySet(new HashSet<>(Arrays.asList(InvalidId.class)));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> schema.initialize())
			.withMessageMatching(
				"Cannot use internal id strategy with custom property getMappingFunctionFor on entity .*");
	}

	@Test
	void shouldPreventIllegalIdTypes() {

		Neo4jMappingContext schema = new Neo4jMappingContext();
		schema.setInitialEntitySet(new HashSet<>(Arrays.asList(InvalidIdType.class)));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> schema.initialize())
			.withMessageMatching("Internally generated ids can only be assigned to one of .*");
	}

	@Test
	void missingIdDefinitionShouldRaiseError() {

		Neo4jMappingContext schema = new Neo4jMappingContext();
		assertThatIllegalStateException()
			.isThrownBy(() -> schema.getPersistentEntity(MissingId.class))
			.withMessage("Missing id property on " + MissingId.class + ".");
	}

	@Test
	void targetTypeOfAssociationsShouldBeKnownToTheMappingContext() {

		Neo4jMappingContext schema = new Neo4jMappingContext();
		Neo4jPersistentEntity<?> bikeNodeEntity = schema.getPersistentEntity(BikeNode.class);
		bikeNodeEntity.doWithAssociations((Association<Neo4jPersistentProperty> association) ->
			assertThat(schema.getRequiredMappingFunctionFor(association.getInverse().getAssociationTargetType()))
				.isNotNull());
	}

	@Test
	void shouldDeriveARelationshipType() {

		Neo4jMappingContext schema = new Neo4jMappingContext();
		Neo4jPersistentEntity<?> bikeNodeEntity = schema.getPersistentEntity(BikeNode.class);
		assertThat(bikeNodeEntity.getRequiredPersistentProperty("renter").getAssociation())
			.isNotNull()
			.satisfies(association -> {
				assertThat(association).isInstanceOf(RelationshipDescription.class);
				RelationshipDescription relationshipDescription = (RelationshipDescription) association;
				assertThat(relationshipDescription.getType()).isEqualTo("RENTER");
			});
	}

	@Test
	void shouldCacheIdGenerators() {

		Neo4jMappingContext schema = new Neo4jMappingContext();
		IdGenerator<?> dummyIdGenerator1 = schema.getOrCreateIdGeneratorOfType(DummyIdGenerator.class);
		IdGenerator<?> dummyIdGenerator2 = schema.getOrCreateIdGeneratorOfType(DummyIdGenerator.class);

		assertThat(dummyIdGenerator1).isSameAs(dummyIdGenerator2);
	}

	@Test
	void complexPropertyWithConverterShouldNotBeConsideredAsAssociation() {

		class ConvertibleTypeConverter implements GenericConverter {
			@Override
			public Set<ConvertiblePair> getConvertibleTypes() {
				// in the real world this should also define the opposite way
				return singleton(new ConvertiblePair(ConvertibleType.class, StringValue.class));
			}

			@Override
			public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
				// no implementation needed for this test
				return null;
			}
		}

		Neo4jMappingContext schema = new Neo4jMappingContext(
			new Neo4jConversions(singleton(new ConvertibleTypeConverter())));
		Neo4jPersistentEntity<?> entity = schema.getPersistentEntity(EntityWithConvertibleProperty.class);

		assertThat(entity.getPersistentProperty("convertibleType").isRelationship()).isFalse();
	}

	@Test
	void complexPropertyWithoutConverterShouldBeConsideredAsAssociation() {

		Neo4jMappingContext schema = new Neo4jMappingContext(new Neo4jConversions());
		Neo4jPersistentEntity<?> entity = schema.getPersistentEntity(EntityWithConvertibleProperty.class);

		assertThat(entity.getPersistentProperty("convertibleType").isRelationship()).isTrue();
	}

	@Test
	void shouldHonourTransientAnnotation() {

		Neo4jMappingContext schema = new Neo4jMappingContext();
		Neo4jPersistentEntity<?> userNodeEntity = schema.getPersistentEntity(UserNode.class);

		assertThat(userNodeEntity.getPersistentProperty("anAnnotatedTransientProperty")).isNull();

		List<String> associations = new ArrayList<>();
		userNodeEntity.doWithAssociations((Association<Neo4jPersistentProperty> a) -> {
			associations.add(a.getInverse().getFieldName());
		});

		assertThat(associations).containsOnly("bikes");
	}

	@Test
	void enumMapKeys() {

		Neo4jMappingContext schema = new Neo4jMappingContext();
		Neo4jPersistentEntity<?> enumRelNodeEntity = schema.getPersistentEntity(EnumRelNode.class);

		List<Neo4jPersistentProperty> associations = new ArrayList<>();
		enumRelNodeEntity
			.doWithAssociations((Association<Neo4jPersistentProperty> a) -> associations.add(a.getInverse()));

		assertThat(associations).hasSize(2);
	}

	static class DummyIdGenerator implements IdGenerator<Void> {

		@Override
		public Void generateId(String primaryLabel, Object entity) {
			return null;
		}
	}

	@Node("User")
	static class UserNode {

		@org.springframework.data.annotation.Id @GeneratedValue
		private long id;

		@Relationship(type = "OWNS")
		List<BikeNode> bikes;

		String name;

		@Transient
		String anAnnotatedTransientProperty;

		@Transient
		List<SomeOtherClass> someOtherTransientThings;

		@Property(name = "firstName")
		String first_name;
	}

	static class SomeOtherClass {

	}

	enum A {
		A1, A2
	}

	enum ExtendedA {

		EA1, EA2 {
			@Override
			public void doNothing() {
			}
		};

		public void doNothing() {

		}
	}

	static class BikeNode {

		@Id
		private String id;

		UserNode owner;

		List<UserNode> renter;

		Map<String, UserNode> dynamicRelationships;

		List<String> someValues;
		String[] someMoreValues;
		byte[] evenMoreValues;
		Map<String, Object> funnyDynamicProperties;
	}

	static class EnumRelNode {

		@Id
		private String id;

		Map<A, UserNode> relA;

		Map<ExtendedA, BikeNode> relEA;
	}

	static class TripNode {

		@Id
		private String id;

		String name;
	}

	static class InvalidId {

		@Id
		@GeneratedValue
		@Property("getMappingFunctionFor")
		private String id;
	}

	static class InvalidIdType {

		@Id @GeneratedValue
		private String id;
	}

	@Node
	static class MissingId {
	}

	@Node
	static class EntityWithConvertibleProperty {

		@Id @GeneratedValue
		private Long id;

		private ConvertibleType convertibleType;
	}

	static class ConvertibleType {
	}
}
