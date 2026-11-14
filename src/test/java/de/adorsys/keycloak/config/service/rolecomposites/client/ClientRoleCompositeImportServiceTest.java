/*-
 * ---license-start
 * keycloak-config-cli
 * ---
 * Copyright (C) 2017 - 2026 adorsys GmbH & Co. KG @ https://adorsys.com
 * ---
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ---license-end
 */

package de.adorsys.keycloak.config.service.rolecomposites.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.RoleRepresentation;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ClientRoleCompositeImportServiceTest {

    private RealmCompositeImport realmCompositeImport;
    private ClientCompositeImport clientCompositeImport;
    private ClientRoleCompositeImportService service;

    @BeforeEach
    void setUp() {
        realmCompositeImport = mock(RealmCompositeImport.class);
        clientCompositeImport = mock(ClientCompositeImport.class);
        service = new ClientRoleCompositeImportService(realmCompositeImport, clientCompositeImport);
    }

    @Test
    void shouldSkipCompositeUpdateForRealmManagementClient() {
        RoleRepresentation role = compositeRole("manage-users", Set.of("view-users"), Map.of(
                "realm-management", List.of("query-users")
        ));

        service.update("test-realm", Map.of("realm-management", List.of(role)));

        verify(realmCompositeImport, never()).update(anyString(), anyString(), any(), any());
        verify(clientCompositeImport, never()).update(anyString(), anyString(), anyString(), any());
    }

    @Test
    void shouldStillUpdateCompositesForRegularClient() {
        RoleRepresentation role = compositeRole("custom-role", Set.of("realm-role"), Map.of(
                "my-client", List.of("other-role")
        ));

        service.update("test-realm", Map.of("my-client", List.of(role)));

        verify(realmCompositeImport).update(eq("test-realm"), eq("my-client"), eq(role), eq(Set.of("realm-role")));
        verify(clientCompositeImport).update(eq("test-realm"), eq("my-client"), eq("custom-role"),
                eq(Map.of("my-client", List.of("other-role"))));
    }

    @Test
    void shouldSkipCompositeUpdateForMasterRealmManagementClient() {
        RoleRepresentation role = compositeRole("manage-users", Set.of("view-users"), null);

        service.update("master", Map.of("my-realm-realm", List.of(role)));

        verify(realmCompositeImport, never()).update(anyString(), anyString(), any(), any());
        verify(clientCompositeImport, never()).update(anyString(), anyString(), anyString(), any());
    }

    private static RoleRepresentation compositeRole(
            String name,
            Set<String> realmComposites,
            Map<String, List<String>> clientComposites
    ) {
        RoleRepresentation.Composites composites = new RoleRepresentation.Composites();
        composites.setRealm(realmComposites);
        composites.setClient(clientComposites);

        RoleRepresentation role = new RoleRepresentation();
        role.setName(name);
        role.setComposite(true);
        role.setComposites(composites);
        return role;
    }
}
