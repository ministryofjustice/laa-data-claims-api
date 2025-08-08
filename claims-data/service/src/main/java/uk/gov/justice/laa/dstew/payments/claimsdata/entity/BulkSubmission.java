package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionDetails;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bulk_submission")
public class BulkSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Type(JsonBinaryType.class)
    @JdbcTypeCode(SqlTypes.JSON)
    private BulkSubmissionDetails data;

    @NotNull
    @Enumerated(EnumType.STRING)
    private BulkSubmissionStatus status;

    private String errorCode;

    private String errorDescription;

    @NotNull
    private String createdByUserId;

    @NotNull
    private Instant createdOn;

    private String updatedByUserId;

    private Instant updatedOn;
}