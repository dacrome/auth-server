package org.osiam.auth.login.ldap;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.inject.Inject;
import javax.naming.ConfigurationException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osiam.auth.configuration.LdapConfiguration;
import org.osiam.resources.scim.Address;
import org.osiam.resources.scim.Email;
import org.osiam.resources.scim.Entitlement;
import org.osiam.resources.scim.Extension;
import org.osiam.resources.scim.Im;
import org.osiam.resources.scim.Name;
import org.osiam.resources.scim.PhoneNumber;
import org.osiam.resources.scim.Photo;
import org.osiam.resources.scim.Role;
import org.osiam.resources.scim.UpdateUser;
import org.osiam.resources.scim.User;
import org.osiam.resources.scim.X509Certificate;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.ldap.ppolicy.PasswordPolicyControl;
import org.springframework.security.ldap.ppolicy.PasswordPolicyResponseControl;
import org.springframework.security.ldap.userdetails.LdapUserDetailsImpl;
import org.springframework.security.ldap.userdetails.LdapUserDetailsMapper;
import org.springframework.stereotype.Component;

@Component
public class OsiamLdapUserContextMapper extends LdapUserDetailsMapper {

    private final Log logger = LogFactory.getLog(LdapUserDetailsMapper.class);
    private String passwordAttributeName = "userPassword";
    private String[] roleAttributes = null;
    
    @Inject
    private LdapConfiguration ldapConfiguration;
    
    @Override
    public OsiamLdapUserDetailsImpl mapUserFromContext(DirContextOperations ctx, String username,
            Collection<? extends GrantedAuthority> authorities) {
        String dn = ctx.getNameInNamespace();

        logger.debug("Mapping user details from context with DN: " + dn);

        LdapUserDetailsImpl.Essence essence = new LdapUserDetailsImpl.Essence();
        essence.setDn(dn);

        Object passwordValue = ctx.getObjectAttribute(passwordAttributeName);

        if (passwordValue != null) {
            essence.setPassword(mapPassword(passwordValue));
        }

        essence.setUsername(username);

        // Map the roles
        for (int i = 0; (roleAttributes != null) && (i < roleAttributes.length); i++) {
            String[] rolesForAttribute = ctx.getStringAttributes(roleAttributes[i]);

            if (rolesForAttribute == null) {
                logger.debug("Couldn't read role attribute '" + roleAttributes[i] + "' for user " + dn);
                continue;
            }

            for (String role : rolesForAttribute) {
                GrantedAuthority authority = createAuthority(role);

                if (authority != null) {
                    essence.addAuthority(authority);
                }
            }
        }

        // Add the supplied authorities

        for (GrantedAuthority authority : authorities) {
            essence.addAuthority(authority);
        }

        // Check for PPolicy data

        PasswordPolicyResponseControl ppolicy = (PasswordPolicyResponseControl) ctx
                .getObjectAttribute(PasswordPolicyControl.OID);

        if (ppolicy != null) {
            essence.setTimeBeforeExpiration(ppolicy.getTimeBeforeExpiration());
            essence.setGraceLoginsRemaining(ppolicy.getGraceLoginsRemaining());
        }

        OsiamLdapUserDetailsImpl ldapUser = new OsiamLdapUserDetailsImpl(
                (LdapUserDetailsImpl) essence.createUserDetails());
        
        return ldapUser;
    }
    
    public User mapUser(DirContextOperations ldapUserData) {

        Extension extension = new Extension(LdapConfiguration.AUTH_EXTENSION);
        extension.addOrUpdateField("origin", LdapConfiguration.LDAP_PROVIDER);

        String userName = ldapUserData.getStringAttribute(ldapConfiguration.getScimLdapAttributes().get("userName"));
        User.Builder builder = new User.Builder(userName)
                .addExtension(extension)
                .setActive(true);

        for (String scimAttribute : ldapConfiguration.getScimLdapAttributes().keySet()) {
            String ldapAttribute = ldapConfiguration.getScimLdapAttributes().get(scimAttribute);
            String ldapValue = ldapUserData.getStringAttribute(ldapAttribute);

            switch (scimAttribute) {
            case "displayName":
                builder.setDisplayName(ldapValue);
                break;
            case "email":
                Email.Builder emailBuilder = new Email.Builder().setValue(ldapValue)
                        .setType(new Email.Type(LdapConfiguration.LDAP_PROVIDER));
                List<Email> emails = new ArrayList<Email>();
                emails.add(emailBuilder.build());
                builder.setEmails(emails);
                break;
            case "entitlement":
                Entitlement.Builder entitlementBuilder = new Entitlement.Builder().setValue(ldapValue)
                        .setType(new Entitlement.Type(LdapConfiguration.LDAP_PROVIDER));
                List<Entitlement> entitlements = new ArrayList<Entitlement>();
                entitlements.add(entitlementBuilder.build());
                builder.setEntitlements(entitlements);
                break;
            case "externalId":
                builder.setExternalId(ldapValue);
                break;
            case "im":
                Im.Builder imBuilder = new Im.Builder().setValue(ldapValue)
                        .setType(new Im.Type(LdapConfiguration.LDAP_PROVIDER));
                List<Im> ims = new ArrayList<Im>();
                ims.add(imBuilder.build());
                builder.setIms(ims);
                break;
            case "locale":
                builder.setLocale(ldapValue);
                break;
            case "nickName":
                builder.setNickName(ldapValue);
                break;
            case "phoneNumber":
                PhoneNumber.Builder phoneNumberBuilder = new PhoneNumber.Builder().setValue(ldapValue)
                        .setType(new PhoneNumber.Type(LdapConfiguration.LDAP_PROVIDER));
                List<PhoneNumber> phoneNumbers = new ArrayList<PhoneNumber>();
                phoneNumbers.add(phoneNumberBuilder.build());
                builder.setPhoneNumbers(phoneNumbers);
                break;
            case "photo":
                Photo.Builder photoBuilder;
                try {
                    photoBuilder = new Photo.Builder().setValue(new URI(ldapValue))
                            .setType(new Photo.Type(LdapConfiguration.LDAP_PROVIDER));
                    List<Photo> photos = new ArrayList<Photo>();
                    photos.add(photoBuilder.build());
                    builder.setPhotos(photos);
                } catch (URISyntaxException e) {
                    new ConfigurationException("Could not map the ldap attibute '"
                            + ldapAttribute + "' with the value '" + ldapValue
                            + "' into an scim photo because the value could not be conferted into an URI.");
                }
                break;
            case "preferredLanguage":
                builder.setPreferredLanguage(ldapValue);
                break;
            case "profileUrl":
                builder.setProfileUrl(ldapValue);
                break;
            case "role":
                Role.Builder roleBuilder = new Role.Builder().setValue(ldapValue)
                        .setType(new Role.Type(LdapConfiguration.LDAP_PROVIDER));
                List<Role> roles = new ArrayList<Role>();
                roles.add(roleBuilder.build());
                builder.setRoles(roles);
                break;
            case "timezone":
                builder.setTimezone(ldapValue);
                break;
            case "title":
                builder.setTitle(ldapValue);
                break;
            case "userType":
                builder.setUserType(ldapValue);
                break;
            case "x509Certificate":
                X509Certificate.Builder x509CertificateBuilder = new X509Certificate.Builder().setValue(ldapValue)
                        .setType(new X509Certificate.Type(LdapConfiguration.LDAP_PROVIDER));
                List<X509Certificate> x509Certificates = new ArrayList<X509Certificate>();
                x509Certificates.add(x509CertificateBuilder.build());
                builder.setX509Certificates(x509Certificates);
                break;
            default:
                if (!scimAttribute.startsWith("address.") && !scimAttribute.startsWith("name.")) {
                    new ConfigurationException("The ldap attibute mapping value '" + scimAttribute
                            + "' could not be reconized as scim attribute.");
                }
                break;
            }
        }

        builder.setAddresses(getAddresses(ldapUserData));
        builder.setName(getName(ldapUserData));

        return builder.build();
    }
    
    public UpdateUser mapUpdateUser(User user, DirContextOperations ldapUserData) {

        UpdateUser.Builder updateBuilder = new UpdateUser.Builder();

        for (String scimAttribute : ldapConfiguration.getScimLdapAttributes().keySet()) {
            String ldapValue = ldapUserData.getStringAttribute(ldapConfiguration.getScimLdapAttributes().get(scimAttribute));

            switch (scimAttribute) {
            case "displayName":
                updateBuilder.updateDisplayName(ldapValue);
                break;
            case "email":
                updateEmail(updateBuilder, user.getEmails(), ldapValue);
                break;
            case "entitlement":
                updateEntitlement(updateBuilder, user.getEntitlements(), ldapValue);
                break;
            case "externalId":
                updateBuilder.updateExternalId(ldapValue);
                break;
            case "im":
                updateIm(updateBuilder, user.getIms(), ldapValue);
                break;
            case "locale":
                updateBuilder.updateLocale(ldapValue);
                break;
            case "nickName":
                updateBuilder.updateNickName(ldapValue);
                break;
            case "phoneNumber":
                updatePhoneNumber(updateBuilder, user.getPhoneNumbers(), ldapValue);
                break;
            case "photo":
                updatePhoto(updateBuilder, user.getPhotos(), ldapValue, scimAttribute);
                break;
            case "preferredLanguage":
                updateBuilder.updatePreferredLanguage(ldapValue);
                break;
            case "profileUrl":
                updateBuilder.updateProfileUrl(ldapValue);
                break;
            case "role":
                updateRole(updateBuilder, user.getRoles(), ldapValue);
                break;
            case "timezone":
                updateBuilder.updateTimezone(ldapValue);
                break;
            case "title":
                updateBuilder.updateTitle(ldapValue);
                break;
            case "userType":
                updateBuilder.updateUserType(ldapValue);
                break;
            case "x509Certificate":
                updateX509Certificate(updateBuilder, user.getX509Certificates(), ldapValue);
                break;
            default:
                if (!scimAttribute.startsWith("address.") && !scimAttribute.startsWith("name.")) {
                    new ConfigurationException("The ldap attibute mapping value '" + scimAttribute
                            + "' could not be reconized as scim attribute.");
                }
            }
        }

        updateAddress(updateBuilder, user.getAddresses(), ldapUserData);
        updateName(updateBuilder, ldapUserData);

        return updateBuilder.build();
    }
    
    private void updateName(UpdateUser.Builder updateBuilder, DirContextOperations ldapUserData) {
        updateBuilder.updateName(getName(ldapUserData));
    }

    private void updateAddress(UpdateUser.Builder updateBuilder, List<Address> addresses,
            DirContextOperations ldapUserData) {
        for (Address address : addresses) {
            if (address.getType() != null && address.getType().toString().equals(LdapConfiguration.LDAP_PROVIDER)) {
                updateBuilder.deleteAddress(address);
            }
        }

        List<Address> newAddresses = getAddresses(ldapUserData);
        if (!newAddresses.isEmpty()) {
            updateBuilder.addAddress(newAddresses.get(0));
        }
    }

    private void updateEmail(UpdateUser.Builder updateBuilder, List<Email> emails, String emailValue) {
        Email newEmail = new Email.Builder().setValue(emailValue).setType(new Email.Type(LdapConfiguration.LDAP_PROVIDER)).build();
        for (Email email : emails) {
            if (email.getType() != null && email.getType().toString().equals(LdapConfiguration.LDAP_PROVIDER)) {
                updateBuilder.deleteEmail(email);
            }
        }
        updateBuilder.addEmail(newEmail);
    }

    private void updateEntitlement(UpdateUser.Builder updateBuilder, List<Entitlement> entitlements, String value) {
        Entitlement newEntitlement = new Entitlement.Builder().setValue(value)
                .setType(new Entitlement.Type(LdapConfiguration.LDAP_PROVIDER)).build();
        for (Entitlement entitlement : entitlements) {
            if (entitlement.getType() != null && entitlement.getType().toString().equals(LdapConfiguration.LDAP_PROVIDER)) {
                updateBuilder.deleteEntitlement(entitlement);
            }
        }
        updateBuilder.addEntitlement(newEntitlement);
    }

    private void updateIm(UpdateUser.Builder updateBuilder, List<Im> ims, String value) {
        Im newIm = new Im.Builder().setValue(value).setType(new Im.Type(LdapConfiguration.LDAP_PROVIDER)).build();
        for (Im im : ims) {
            if (im.getType() != null && im.getType().toString().equals(LdapConfiguration.LDAP_PROVIDER)) {
                updateBuilder.deleteIms(im);
            }
        }
        updateBuilder.addIms(newIm);
    }

    private void updatePhoneNumber(UpdateUser.Builder updateBuilder, List<PhoneNumber> phoneNumbers, String value) {
        PhoneNumber newPhoneNumber = new PhoneNumber.Builder().setValue(value)
                .setType(new PhoneNumber.Type(LdapConfiguration.LDAP_PROVIDER)).build();
        for (PhoneNumber phoneNumber : phoneNumbers) {
            if (phoneNumber.getType() != null && phoneNumber.getType().toString().equals(LdapConfiguration.LDAP_PROVIDER)) {
                updateBuilder.deletePhoneNumber(phoneNumber);
            }
        }
        updateBuilder.addPhoneNumber(newPhoneNumber);
    }

    private void updatePhoto(UpdateUser.Builder updateBuilder, List<Photo> photos, String value, String scimAttribute) {
        try {
            Photo newPhoto = new Photo.Builder().setValue(new URI(value)).setType(new Photo.Type(LdapConfiguration.LDAP_PROVIDER))
                    .build();
            for (Photo photo : photos) {
                if (photo.getType() != null && photo.getType().toString().equals(LdapConfiguration.LDAP_PROVIDER)) {
                    updateBuilder.deletePhoto(photo);
                }
            }
            updateBuilder.addPhoto(newPhoto);
        } catch (URISyntaxException e) {
            new ConfigurationException("Could not map the ldap attibute '"
                    + ldapConfiguration.getScimLdapAttributes().get(scimAttribute) + "' with the value '" + value
                    + "' into an scim photo because the value could not be conferted into an URI.");
        }
    }

    private void updateRole(UpdateUser.Builder updateBuilder, List<Role> roles, String value) {
        Role newRole = new Role.Builder().setValue(value).setType(new Role.Type(LdapConfiguration.LDAP_PROVIDER)).build();
        for (Role role : roles) {
            if (role.getType() != null && role.getType().toString().equals(LdapConfiguration.LDAP_PROVIDER)) {
                updateBuilder.deleteRole(role);
            }
        }
        updateBuilder.addRole(newRole);
    }

    private void updateX509Certificate(UpdateUser.Builder updateBuilder, List<X509Certificate> x509Certificates,
            String value) {
        X509Certificate newX509Certificate = new X509Certificate.Builder().setValue(value)
                .setType(new X509Certificate.Type(LdapConfiguration.LDAP_PROVIDER)).build();
        for (X509Certificate x509Certificate : x509Certificates) {
            if (x509Certificate.getType() != null && x509Certificate.getType().toString().equals(LdapConfiguration.LDAP_PROVIDER)) {
                updateBuilder.deleteX509Certificate(x509Certificate);
            }
        }
        updateBuilder.addX509Certificate(newX509Certificate);
    }
    
    private List<Address> getAddresses(DirContextOperations ldapUserData) {
        List<Address> addresses = new ArrayList<Address>();
        Address.Builder builder = new Address.Builder();
        boolean addressFound = false;

        for (String scimAttribute : ldapConfiguration.getScimLdapAttributes().keySet()) {
            String ldapValue = ldapUserData.getStringAttribute(ldapConfiguration.getScimLdapAttributes().get(scimAttribute));
            if (!scimAttribute.startsWith("address.")) {
                continue;
            }
            addressFound = true;
            switch (scimAttribute) {
            case "address.country":
                builder.setCountry(ldapValue);
                break;
            case "address.formatted":
                builder.setFormatted(ldapValue);
                break;
            case "address.locality":
                builder.setLocality(ldapValue);
                break;
            case "address.postalCode":
                builder.setPostalCode(ldapValue);
                break;
            case "address.region":
                builder.setRegion(ldapValue);
                break;
            case "address.streetAddress":
                builder.setStreetAddress(ldapValue);
                break;
            default:
                new ConfigurationException("The ldap attibute mapping value '" + scimAttribute
                        + "' could not be reconized as scim attribute.");
                break;
            }
        }

        if (addressFound) {
            builder.setType(new Address.Type(LdapConfiguration.LDAP_PROVIDER));
            addresses.add(builder.build());
        }
        return addresses;
    }
    
    private Name getName(DirContextOperations ldapUserData) {
        Name name = null;
        Name.Builder builder = new Name.Builder();
        boolean nameFound = false;

        for (String scimAttribute : ldapConfiguration.getScimLdapAttributes().keySet()) {
            String ldapValue = ldapUserData.getStringAttribute(ldapConfiguration.getScimLdapAttributes().get(scimAttribute));
            if (!scimAttribute.startsWith("name.")) {
                continue;
            }
            nameFound = true;
            switch (scimAttribute) {
            case "name.familyName":
                builder.setFamilyName(ldapValue);
                break;
            case "name.formatted":
                builder.setFormatted(ldapValue);
                break;
            case "name.givenName":
                builder.setGivenName(ldapValue);
                break;
            case "name.honorificPrefix":
                builder.setHonorificPrefix(ldapValue);
                break;
            case "name.honorificSuffix":
                builder.setHonorificSuffix(ldapValue);
                break;
            case "name.middleName":
                builder.setMiddleName(ldapValue);
                break;
            default:
                new ConfigurationException("The ldap attibute mapping value '" + scimAttribute
                        + "' could not be reconized as scim attribute.");
                break;
            }
        }

        if (nameFound) {
            name = builder.build();
        }
        return name;
    }
    
}
