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

package de.adorsys.keycloak.config.service;

import de.adorsys.keycloak.config.exception.ImportProcessingException;
import de.adorsys.keycloak.config.model.RealmImport;
import de.adorsys.keycloak.config.properties.ImportConfigProperties;
import de.adorsys.keycloak.config.repository.RoleRepository;
import de.adorsys.keycloak.config.service.rolecomposites.client.ClientRoleCompositeImportService;
import de.adorsys.keycloak.config.service.rolecomposites.realm.RealmRoleCompositeImportService;
import de.adorsys.keycloak.config.service.state.StateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.RolesRepresentation;

import java.util.List;
import java.util.Map;

import static de.adorsys.keycloak.config.properties.ImportConfigProperties.ImportManagedProperties.ImportManagedPropertiesValues.FULL;
import static de.adorsys.keycloak.config.properties.ImportConfigProperties.ImportManagedProperties.ImportManagedPropertiesValues.NO_DELETE;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleImportServiceTest {

    private RoleRepository roleRepository;
    private ImportConfigProperties importConfigProperties;
    private ImportConfigProperties.ImportManagedProperties managedProperties;
    private ImportConfigProperties.ImportRemoteStateProperties remoteStateProperties;
    private ClientRoleCompositeImportService clientRoleCompositeImport;

    private RoleImportService service;

    @BeforeEach
    void setUp() {
        roleRepository = mock(RoleRepository.class);
        importConfigProperties = mock(ImportConfigProperties.class);
        managedProperties = mock(ImportConfigProperties.ImportManagedProperties.class);
        remoteStateProperties = mock(ImportConfigProperties.ImportRemoteStateProperties.class);
        clientRoleCompositeImport = mock(ClientRoleCompositeImportService.class);
        RealmRoleCompositeImportService realmRoleCompositeImport = mock(RealmRoleCompositeImportService.class);
        StateService stateService = mock(StateService.class);

        when(importConfigProperties.getManaged()).thenReturn(managedProperties);
        when(managedProperties.getRole()).thenReturn(NO_DELETE);
        when(importConfigProperties.getRemoteState()).thenReturn(remoteStateProperties);
        when(remoteStateProperties.isEnabled()).thenReturn(false);
        when(importConfigProperties.isParallel()).thenReturn(false);

        service = new RoleImportService(
                realmRoleCompositeImport,
                clientRoleCompositeImport,
                roleRepository,
                importConfigProperties,
                stateService
        );
    }

    @Test
    void shouldNotUpdateRoleOfRealmManagementClient() {
        RoleRepresentation existing = role("manage-users", "built-in");
        when(roleRepository.getClientRoles("test-realm"))
                .thenReturn(Map.of("realm-management", List.of(existing)));

        RealmImport realmImport = realmWithClientRoles("test-realm", Map.of(
                "realm-management", List.of(role("manage-users", "from-export"))
        ));

        service.doImport(realmImport);

        verify(roleRepository, never()).updateClientRole(any(), any(), any());
    }

    @Test
    void shouldStillUpdateRoleOfRegularClient() {
        RoleRepresentation existing = role("custom-role", "old");
        when(roleRepository.getClientRoles("test-realm"))
                .thenReturn(Map.of("my-client", List.of(existing)));

        RealmImport realmImport = realmWithClientRoles("test-realm", Map.of(
                "my-client", List.of(role("custom-role", "new"))
        ));

        service.doImport(realmImport);

        verify(roleRepository).updateClientRole(eq("test-realm"), eq("my-client"), any());
    }

    @Test
    void shouldNotDeleteRealmManagementRolesWhenManagedFull() {
        when(managedProperties.getRole()).thenReturn(FULL);

        RoleRepresentation builtIn = role("manage-users", "built-in");
        RoleRepresentation custom = role("custom-role", "application role");
        when(roleRepository.getClientRoles("test-realm")).thenReturn(Map.of(
                "realm-management", List.of(builtIn),
                "my-client", List.of(custom)
        ));

        RealmImport realmImport = realmWithClientRoles("test-realm", Map.of(
                "my-client", List.of()
        ));

        service.doImport(realmImport);

        verify(roleRepository, never()).deleteClientRole(eq("test-realm"), eq("realm-management"), any());
        verify(roleRepository).deleteClientRole(eq("test-realm"), eq("my-client"), eq(custom));
    }

    @Test
    void shouldNotCreateRoleOnRealmManagementClient() {
        when(roleRepository.getClientRoles("test-realm"))
                .thenReturn(Map.of("realm-management", List.of()));

        RealmImport realmImport = realmWithClientRoles("test-realm", Map.of(
                "realm-management", List.of(role("custom-admin", "not a built-in role"))
        ));

        assertThrows(ImportProcessingException.class, () -> service.doImport(realmImport));

        verify(roleRepository, never()).createClientRole(any(), any(), any());
        verify(roleRepository, never()).updateClientRole(any(), any(), any());
    }

    private static RoleRepresentation role(String name, String description) {
        RoleRepresentation role = new RoleRepresentation();
        role.setName(name);
        role.setDescription(description);
        return role;
    }

    private static RealmImport realmWithClientRoles(String realmName, Map<String, List<RoleRepresentation>> clientRoles) {
        RolesRepresentation roles = new RolesRepresentation();
        roles.setClient(clientRoles);

        RealmImport realmImport = new RealmImport();
        realmImport.setRealm(realmName);
        realmImport.setRoles(roles);
        return realmImport;
    }
}
