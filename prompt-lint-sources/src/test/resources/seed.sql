-- mixed seed file
INSERT INTO other_table (id, note) VALUES ('x', 'ignore me');

INSERT INTO prompts (agent_id, system_text, user_text, agent_type) VALUES
  ('extractor', 'You extract structured names from the supplied text.', 'Source: {{text}}', 'extraction'),
  ('greeter', 'You greet the user warmly and concisely.', 'Name: {{name}}', 'formatting');
