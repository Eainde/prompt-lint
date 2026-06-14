INSERT INTO prompts (agent_id, system_text, user_text, agent_type) VALUES
  ('sql-extractor',
   'You extract every full person name from the source text exactly as written. Do not infer names. Use only the supplied text. Return a JSON array of strings.',
   'Source: {{text}}',
   'extraction');
