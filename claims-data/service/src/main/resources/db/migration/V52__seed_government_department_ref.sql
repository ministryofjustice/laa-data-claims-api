-- Seed the governed government department reference data (DSTEW-2358).
-- Ids below are pre-generated UUIDv7 values. The seed actor recorded in
-- created_by_user_id is the static value 'DSTEW-2358-migration'.

INSERT INTO claims.government_department_ref
(id, government_department_code, display_label, is_active, display_order, created_by_user_id, created_on)
VALUES
    ('01a12f93-2a8c-7464-895e-000000000001', 'ATTORNEY_GENERALS_OFFICE',
     'Attorney General''s Office', TRUE, 10, 'DSTEW-2358-migration', now()),
    ('01a12f93-2a8c-7464-895e-000000000002', 'CABINET_OFFICE',
     'Cabinet Office', TRUE, 20, 'DSTEW-2358-migration', now()),
    ('01a12f93-2a8c-7464-895e-000000000003', 'DEPARTMENT_DEVOLVED_TO_WALES',
     'Department devolved to Wales', TRUE, 30, 'DSTEW-2358-migration', now()),
    ('01a12f93-2a8c-7464-895e-000000000004', 'DEPARTMENT_FOR_BUSINESS_AND_TRADE',
     'Department for Business and Trade', TRUE, 40, 'DSTEW-2358-migration', now()),
    ('01a12f93-2a8c-7464-895e-000000000005', 'DEPARTMENT_FOR_CULTURE_MEDIA_AND_SPORT',
     'Department for Culture, Media, and Sport', TRUE, 50, 'DSTEW-2358-migration', now()),
    ('01a12f93-2a8c-7464-895e-000000000006', 'DEPARTMENT_FOR_EDUCATION',
     'Department for Education', TRUE, 60, 'DSTEW-2358-migration', now()),
    ('01a12f93-2a8c-7464-895e-000000000007', 'DEPARTMENT_FOR_ENERGY_SECURITY_AND_NET_ZERO',
     'Department for Energy Security and Net Zero', TRUE, 70, 'DSTEW-2358-migration', now()),
    ('01a12f93-2a8c-7464-895e-000000000008', 'DEPARTMENT_FOR_ENVIRONMENT_FOOD_AND_RURAL_AFFAIRS',
     'Department for Environment, Food and Rural Affairs', TRUE, 80, 'DSTEW-2358-migration', now()),
    ('01a12f93-2a8c-7464-895e-000000000009', 'DEPARTMENT_FOR_HEALTH_AND_SOCIAL_CARE',
     'Department for Health and Social Care', TRUE, 90, 'DSTEW-2358-migration', now()),
    ('01a12f93-2a8c-7464-895e-000000000010', 'DEPARTMENT_FOR_HOUSING_COMMUNITIES_AND_LOCAL_GOVERNMENT',
     'Department for Housing, Communities and Local Government', TRUE, 100, 'DSTEW-2358-migration', now()),
    ('01a12f93-2a8c-7464-895e-000000000011', 'DEPARTMENT_FOR_SCIENCE_INNOVATION_AND_TECHNOLOGY',
     'Department for Science, Innovation and Technology', TRUE, 110, 'DSTEW-2358-migration', now()),
    ('01a12f93-2a8c-7464-895e-000000000012', 'DEPARTMENT_FOR_TRANSPORT',
     'Department for Transport', TRUE, 120, 'DSTEW-2358-migration', now()),
    ('01a12f93-2a8c-7464-895e-000000000013', 'DEPARTMENT_FOR_WORK_AND_PENSIONS',
     'Department for Work and Pensions', TRUE, 130, 'DSTEW-2358-migration', now()),
    ('01a12f93-2a8c-7464-895e-000000000014', 'FOREIGN_COMMONWEALTH_AND_DEVELOPMENT_OFFICE',
     'Foreign, Commonwealth and Development Office', TRUE, 140, 'DSTEW-2358-migration', now()),
    ('01a12f93-2a8c-7464-895e-000000000015', 'HM_TREASURY',
     'HM Treasury', TRUE, 150, 'DSTEW-2358-migration', now()),
    ('01a12f93-2a8c-7464-895e-000000000016', 'HOME_OFFICE',
     'Home Office', TRUE, 160, 'DSTEW-2358-migration', now()),
    ('01a12f93-2a8c-7464-895e-000000000017', 'MINISTRY_OF_DEFENCE',
     'Ministry of Defence', TRUE, 170, 'DSTEW-2358-migration', now()),
    ('01a12f93-2a8c-7464-895e-000000000018', 'MINISTRY_OF_JUSTICE',
     'Ministry of Justice', TRUE, 180, 'DSTEW-2358-migration', now());
