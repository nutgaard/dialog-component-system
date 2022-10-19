insert into dialog(id, source, owner, owner_type) VALUES
    ('2GEPrKqQxNEUJdUlb4spnHhpp2N', 'basic_dialog', '12345678910', 'ExternalUser'),
    ('2GEPrNJS6buhAhFUhaFpKLUdmzs', 'basic_dialog', '98765432110', 'ExternalOrganization'),
    ('2GEPrMWCRa4jkqDJ1ISCe6HzECW', 'basic_dialog', '12345678910', 'ExternalUser'),
    ('2GEPrMOSmKcpJWTuPx2EF7h9RkR', 'basic_dialog', '98765432110', 'ExternalOrganization');

insert into message(id, dialog_id, actor, actor_type, content) VALUES
    ('2GEPzB9njq94uU1DC1aVb9dPtlk', '2GEPrKqQxNEUJdUlb4spnHhpp2N', '12345678910', 'ExternalUser', 'This is a question from a user?'),
    ('2GEPz7FfTKOfQKr4WGM9WTAvcpt', '2GEPrKqQxNEUJdUlb4spnHhpp2N', 'Frida', 'System', 'Automatic response from system'),
    ('2GEPzA7HhXERDGtfAR06HbAeqri', '2GEPrKqQxNEUJdUlb4spnHhpp2N', 'Z123456', 'Employee', 'Our AI is wrong. Here is some info.'),

    ('2GEPzCX5ZWDVsB5kbkSMmk3Dsy7', '2GEPrNJS6buhAhFUhaFpKLUdmzs', 'Z123456', 'Employee', 'Question to an organization?'),
    ('2GEPzCM9shqtqhF8DdBlhWQvVjX', '2GEPrNJS6buhAhFUhaFpKLUdmzs', '98765432110', 'ExternalOrganization', 'Some good reply'),
    ('2GEPzBj4Nfoir1ipCTd0ZLROWms', '2GEPrNJS6buhAhFUhaFpKLUdmzs', 'Z123456', 'Employee', 'Ok, money on the way'),

    ('2GEPz81UMtKiShTOaVxf6TEjarr', '2GEPrMWCRa4jkqDJ1ISCe6HzECW', '12345678910', 'ExternalUser', 'Another question from a user?'),
    ('2GEPzC42iTlFTMKpbNbUqRZuQs6', '2GEPrMWCRa4jkqDJ1ISCe6HzECW', 'Frida', 'System', 'Automatic response from system'),
    ('2GEPzCwy5Uv3xnChg56PkJYLYBp', '2GEPrMWCRa4jkqDJ1ISCe6HzECW', 'Z123456', 'Employee', 'Our AI is wrong. Here is some info.'),

    ('2GEPzBSeuSVaniQokK949QEmMhU', '2GEPrMOSmKcpJWTuPx2EF7h9RkR', '98765432110', 'ExternalOrganization', 'Question from organization??'),
    ('2GEPz92Z2IkD80A1KkT6jz2sQCK', '2GEPrMOSmKcpJWTuPx2EF7h9RkR', 'Z123456', 'Employee', 'Answer from first responder.'),
    ('2GEPzDVnWGbtWihclPC3avtJPfM', '2GEPrMOSmKcpJWTuPx2EF7h9RkR', 'Z999999', 'Employee', 'Answer from subject matter expert');


select add_component_to_dialog('2GEPrKqQxNEUJdUlb4spnHhpp2N', ('{ "type": "CLOSED", "time": "' || now() || '" }')::jsonb);